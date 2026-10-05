package com.sentinel.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One aircraft record from the adsb.lol {@code /v2} response.
 *
 * Field names follow the upstream API. Unknown fields are ignored so
 * upstream additions never break parsing. All fields are nullable —
 * the feed omits anything a receiver did not decode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdsbAircraft(
        @JsonProperty("hex") String hex,
        @JsonProperty("flight") String flight,
        @JsonProperty("lat") Double lat,
        @JsonProperty("lon") Double lon,
        /** Barometric altitude in feet — a number, or the string {@code "ground"}. */
        @JsonProperty("alt_baro") Object altBaro,
        @JsonProperty("gs") Double groundSpeedKts,
        @JsonProperty("track") Double trackDeg,
        /** ICAO aircraft type designator, e.g. {@code "B738"}. */
        @JsonProperty("t") String type,
        /** ADS-B emitter category code. */
        @JsonProperty("category") String category) {

    /** Altitude in feet, or {@code null} when on the ground / not reported. */
    public Double altitudeFt() {
        if (altBaro instanceof Number n) {
            return n.doubleValue();
        }
        return null; // "ground" or missing
    }

    public String callsign() {
        return flight == null ? null : flight.trim();
    }

    /** Minimum fields required for a usable position report. */
    public boolean hasPosition() {
        return hex != null && !hex.isBlank() && lat != null && lon != null;
    }
}
