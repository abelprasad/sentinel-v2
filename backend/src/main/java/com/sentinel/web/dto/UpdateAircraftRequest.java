package com.sentinel.web.dto;

import jakarta.validation.constraints.Size;

/** Mutable aircraft fields. ICAO hex is the identity and is never changed. */
public record UpdateAircraftRequest(
        @Size(max = 16, message = "callsign must be at most 16 characters") String callsign,
        @Size(max = 64, message = "category must be at most 64 characters") String category) {
}
