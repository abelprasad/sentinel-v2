package com.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinel.config.SentinelProperties;
import com.sentinel.security.JwtTokenService.AuthenticatedUser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!";

    private static JwtTokenService service(String secret, long expirationMs) {
        return new JwtTokenService(new SentinelProperties(
                new SentinelProperties.Adsb("http://localhost", 1000, 1000, 1000),
                new SentinelProperties.Anomaly(3.0, 1, 1, 1, 1, 1.5),
                new SentinelProperties.Llm("groq", "model", null, false, 1000, 1000),
                new SentinelProperties.Security(secret, expirationMs, ""),
                new SentinelProperties.Prune(7, "0 0 3 * * *")));
    }

    @Test
    void generateAndParseRoundTrip() {
        JwtTokenService svc = service(SECRET, 60_000);
        String token = svc.generate("abel", Role.ADMIN);

        Optional<AuthenticatedUser> parsed = svc.parse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().username()).isEqualTo("abel");
        assertThat(parsed.get().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void tamperedTokenRejected() {
        JwtTokenService svc = service(SECRET, 60_000);
        String token = svc.generate("abel", Role.ADMIN);

        // Corrupt the payload segment but keep the original signature.
        String[] parts = token.split("\\.");
        String payload = parts[1];
        char mid = payload.charAt(payload.length() / 2);
        String tamperedPayload = payload.substring(0, payload.length() / 2)
                + (mid == 'a' ? 'b' : 'a')
                + payload.substring(payload.length() / 2 + 1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(svc.parse(tampered)).isEmpty();
    }

    @Test
    void tokenFromDifferentSecretRejected() {
        JwtTokenService svc = service(SECRET, 60_000);
        JwtTokenService other = service("a-completely-different-32-byte-secret!!", 60_000);

        assertThat(other.parse(svc.generate("abel", Role.ADMIN))).isEmpty();
    }

    @Test
    void expiredTokenRejected() {
        JwtTokenService svc = service(SECRET, -1_000); // already expired at issue time

        assertThat(svc.parse(svc.generate("abel", Role.ADMIN))).isEmpty();
    }

    @Test
    void garbageTokensRejectedWithoutThrowing() {
        JwtTokenService svc = service(SECRET, 60_000);

        assertThat(svc.parse(null)).isEmpty();
        assertThat(svc.parse("")).isEmpty();
        assertThat(svc.parse("not.a.token")).isEmpty();
        assertThat(svc.parse("garbage")).isEmpty();
    }

    @Test
    void missingSecretRefusesToBoot() {
        assertThatThrownBy(() -> service(null, 60_000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("   ", 60_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecretRefusesToBoot() {
        assertThatThrownBy(() -> service("too-short", 60_000)).isInstanceOf(IllegalStateException.class);
    }
}
