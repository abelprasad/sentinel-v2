package com.sentinel.web.dto;

import java.time.Instant;

/** Public system status: liveness plus aggregate counters. No sensitive detail. */
public record StatusDto(
        String status,
        String service,
        Instant time,
        long aircraftTracked,
        long activeTracks,
        long eventsLastHour,
        long unacknowledgedAnomalies,
        long anomaliesLast24h) {
}
