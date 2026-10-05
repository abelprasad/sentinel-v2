package com.sentinel.security;

import com.sentinel.config.SentinelProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates JWTs for admin authentication.
 *
 * <p>Fail-closed by construction: the service refuses to instantiate unless a
 * signing secret of at least 256 bits is configured. No secret, no boot.
 */
@Service
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";
    private static final int MIN_SECRET_BYTES = 32; // 256 bits for HS256

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenService(SentinelProperties props) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "sentinel.security.jwt-secret (JWT_SECRET) is not set — refusing to boot without a JWT signing secret");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = props.security().jwtExpirationMs();
    }

    /** Issues a signed token carrying the username and server-side role. */
    public String generate(String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Validates a token and extracts the principal. Never throws — returns
     * empty for expired, tampered, or malformed tokens.
     */
    public Optional<AuthenticatedUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims claims = jws.getPayload();
            String username = claims.getSubject();
            String roleName = claims.get(ROLE_CLAIM, String.class);
            if (username == null || username.isBlank() || roleName == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(username, Role.valueOf(roleName)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Principal extracted from a validated token. */
    public record AuthenticatedUser(String username, Role role) {}
}
