package com.sentinel.ingestion;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Decides whether a fresh ADS-B report is worth persisting.
 *
 * <p>v1 wrote a {@code flight_event} row for every aircraft on every
 * 30s poll unconditionally — a parked aircraft produced 2,880
 * identical rows a day. This skips reports that add no information:
 * if the previous event is still inside the dedup window and no
 * field moved beyond its tolerance, the report is dropped.
 *
 * <p>Pure logic, no I/O — trivially unit-testable.
 */
@Component
public class DedupDecider {

    /** Position change below this (degrees) counts as stationary. ~220m at the equator. */
    static final double POSITION_TOLERANCE_DEG = 0.002;
    static final double ALTITUDE_TOLERANCE_FT = 100.0;
    static final double SPEED_TOLERANCE_KTS = 5.0;
    static final double HEADING_TOLERANCE_DEG = 5.0;

    /**
     * @param previous the latest persisted event for this aircraft, or null if none
     * @param current  the fresh report
     * @param window   dedup window (from {@code sentinel.anomaly.dedup-minutes})
     * @param now      poll timestamp
     * @return true if the report should be persisted
     */
    public boolean shouldPersist(FlightEvent previous, AdsbAircraft current, Duration window, Instant now) {
        if (previous == null) {
            return true;
        }
        if (Duration.between(previous.getRecordedAt(), now).compareTo(window) > 0) {
            return true; // stale — keep a heartbeat even if nothing moved
        }
        return changed(previous, current);
    }

    private boolean changed(FlightEvent previous, AdsbAircraft current) {
        return differs(previous.getLat(), current.lat(), POSITION_TOLERANCE_DEG)
                || differs(previous.getLon(), current.lon(), POSITION_TOLERANCE_DEG)
                || differs(previous.getAltitudeFt(), current.altitudeFt(), ALTITUDE_TOLERANCE_FT)
                || differs(previous.getSpeedKts(), current.groundSpeedKts(), SPEED_TOLERANCE_KTS)
                || differs(previous.getHeadingDeg(), current.trackDeg(), HEADING_TOLERANCE_DEG);
    }

    /**
     * Null means "not reported". A field counts as changed when exactly
     * one side is null, or both are present and differ beyond tolerance.
     */
    private boolean differs(Double oldValue, Double newValue, double tolerance) {
        if (oldValue == null || newValue == null) {
            return oldValue != newValue;
        }
        return Math.abs(oldValue - newValue) > tolerance;
    }
}
