package com.sentinel.web.dto;

import java.time.Instant;

/** One telemetry point on a track. Nullable fields stay null — the client decides how to render gaps. */
public record TrackPointDto(
        Double lat,
        Double lon,
        Double altitudeFt,
        Double speedKts,
        Double headingDeg,
        Instant recordedAt) {
}
