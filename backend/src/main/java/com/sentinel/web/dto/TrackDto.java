package com.sentinel.web.dto;

import java.util.List;

/** An aircraft plus its recent track points, oldest first. Capped server-side. */
public record TrackDto(
        AircraftDto aircraft,
        List<TrackPointDto> points) {
}
