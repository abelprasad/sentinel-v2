package com.sentinel.web.dto;

import java.time.Instant;

/** Public view of a tracked aircraft. No internal identifiers leak. */
public record AircraftDto(
        String icaoHex,
        String callsign,
        String category,
        String trackState,
        Instant firstSeen,
        Instant lastSeen) {
}
