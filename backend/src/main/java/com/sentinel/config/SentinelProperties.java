package com.sentinel.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated configuration for all SENTINEL domain settings.
 * Bound from {@code sentinel.*} in application.yaml; every value
 * overridable via environment variable.
 */
@Validated
@ConfigurationProperties(prefix = "sentinel")
public record SentinelProperties(
        Adsb adsb,
        Anomaly anomaly,
        Llm llm,
        Security security,
        Prune prune) {

    public record Adsb(
            @NotBlank String url,
            @Min(1000) long pollIntervalMs,
            @Min(1000) int connectTimeoutMs,
            @Min(1000) int readTimeoutMs) {
    }

    public record Anomaly(
            double threshold,
            @Min(1) int dedupMinutes,
            @Min(1) int minEventsForBaseline) {
    }

    public record Llm(
            String provider,
            String model,
            String apiKey,
            boolean enabled,
            @Min(1000) int connectTimeoutMs,
            @Min(1000) int readTimeoutMs) {

        /** LLM is usable only when explicitly enabled AND a key is present. */
        public boolean isUsable() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Security(
            String jwtSecret,
            @Min(60000) long jwtExpirationMs,
            String corsAllowedOrigins) {
    }

    public record Prune(
            @Min(1) int retentionDays,
            @NotBlank String cron) {
    }
}
