package com.sentinel.web.dto;

import java.time.Instant;

/**
 * Public view of a flagged anomaly. Carries the per-dimension z-scores so
 * the UI can explain <em>why</em> without recomputing statistics.
 */
public record AnomalyDto(
        Long id,
        String icaoHex,
        String callsign,
        double score,
        Double zAltitude,
        Double zSpeed,
        Double zHeading,
        Double zPosition,
        String explanation,
        String explanationSrc,
        Long parentAnomalyId,
        boolean acknowledged,
        boolean escalated,
        Instant flaggedAt) {
}
