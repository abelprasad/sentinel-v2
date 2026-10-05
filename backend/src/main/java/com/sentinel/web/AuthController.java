package com.sentinel.web;

import com.sentinel.security.AppUserRepository;
import com.sentinel.security.JwtTokenService;
import com.sentinel.security.LoginRateLimiter;
import com.sentinel.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication. Exactly one endpoint: login. There is no registration —
 * users are seeded from env vars (see {@code AdminSeeder}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final LoginRateLimiter rateLimiter;

    public AuthController(
            AppUserRepository users,
            PasswordEncoder encoder,
            JwtTokenService tokens,
            LoginRateLimiter rateLimiter) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String clientIp = clientIp(http);
        if (!rateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ErrorDto.of(
                            "rate_limited", "Too many login attempts — try again later", "/api/auth/login"));
        }
        var user = users.findByUsername(request.username());
        // Identical response for unknown user vs wrong password: no user enumeration.
        if (user.isEmpty() || !encoder.matches(request.password(), user.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorDto.of(
                            "invalid_credentials", "Invalid username or password", "/api/auth/login"));
        }
        String token = tokens.generate(user.get().getUsername(), user.get().getRole());
        return ResponseEntity.ok(
                new LoginResponse(token, user.get().getUsername(), user.get().getRole()));
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String username, Role role) {}
}
