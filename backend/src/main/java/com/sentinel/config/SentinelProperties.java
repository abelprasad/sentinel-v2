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
            /** Z-score threshold for flagging. v1 used 0.7 on a normalized deviation; v2 uses proper z-scores. */
            double threshold,
            @Min(1) int dedupMinutes,
            @Min(1) int minEventsForBaseline,
            /** Sliding window for rolling baselines; stats reset when an event arrives past the edge. */
            @Min(1) int baselineWindowHours,
            /** Minimum gap between standalone alerts for one aircraft. */
            @Min(1) int cooldownMinutes,
            /** A score this many times the previous anomaly's escalates instead of cooling down. */
            double escalationMultiplier) {
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
