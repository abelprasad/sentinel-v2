package com.sentinel.web.dto;

import java.time.Instant;

/** Admin view of a rolling baseline: means, sample counts, and window state. */
public record BaselineDto(
        String icaoHex,
        Double avgAltitudeFt,
        int altitudeCount,
        Double avgSpeedKts,
        int speedCount,
        Double avgHeadingDeg,
        int headingCount,
        Instant windowStart,
        boolean ready) {
}
