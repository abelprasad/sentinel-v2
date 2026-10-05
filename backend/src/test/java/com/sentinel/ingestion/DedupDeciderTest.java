package com.sentinel.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for dedup decisions — no Spring, no DB.
 */
class DedupDeciderTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    private DedupDecider decider;
    private Instant now;

    @BeforeEach
    void setUp() {
        decider = new DedupDecider();
        now = Instant.now();
    }

    private FlightEvent event(double lat, double lon, Double alt, Double speed, Double heading, Instant recordedAt) {
        FlightEvent e = new FlightEvent(1L, recordedAt);
        e.setLat(lat);
        e.setLon(lon);
        e.setAltitudeFt(alt);
        e.setSpeedKts(speed);
        e.setHeadingDeg(heading);
        return e;
    }

    private AdsbAircraft report(double lat, double lon, Object altBaro, Double speed, Double heading) {
        return new AdsbAircraft("a1b2c3", "TST1", lat, lon, altBaro, speed, heading, "B738", "A3");
    }

    @Test
    void persistsWhenNoPreviousEvent() {
        AdsbAircraft current = report(40.0, -75.0, 35000, 450.0, 90.0);
        assertThat(decider.shouldPersist(null, current, WINDOW, now)).isTrue();
    }

    @Test
    void persistsWhenPreviousIsStale() {
        FlightEvent previous = event(40.0, -75.0, 35000.0, 450.0, 90.0, now.minus(Duration.ofMinutes(6)));
        AdsbAircraft current = report(40.0, -75.0, 35000, 450.0, 90.0);
        assertThat(decider.shouldPersist(previous, current, WINDOW, now)).isTrue();
    }

    @Test
    void skipsIdenticalReportInsideWindow() {
        FlightEvent previous = event(40.0, -75.0, 35000.0, 450.0, 90.0, now.minus(Duration.ofMinutes(1)));
        AdsbAircraft current = report(40.0, -75.0, 35000, 450.0, 90.0);
        assertThat(decider.shouldPersist(previous, current, WINDOW, now)).isFalse();
    }

    @Test
    void persistsWhenPositionMoved() {
        FlightEvent previous = event(40.0, -75.0, 35000.0, 450.0, 90.0, now.minus(Duration.ofMinutes(1)));
        AdsbAircraft current = report(40.01, -75.0, 35000, 450.0, 90.0); // ~1.1km
        assertThat(decider.shouldPersist(previous, current, WINDOW, now)).isTrue();
    }

    @Test
    void skipsJitterInsideTolerances() {
        FlightEvent previous = event(40.0, -75.0, 35000.0, 450.0, 90.0, now.minus(Duration.ofMinutes(1)));
        AdsbAircraft current = report(40.0005, -75.0005, 35050, 452.0, 92.0);
        assertThat(decider.shouldPersist(previous, current, WINDOW, now)).isFalse();
    }

    @Test
    void persistsWhenAltitudeChanged() {
        FlightEvent previous = event(40.0, -75.0, 35000.0, 450.0, 90.0, now.minus(Duration.ofMinutes(1)));
        AdsbAircraft current = report(40.0, -75.0, 34000, 450.0, 90.0);
        assertThat(decider.shouldPersist(previous, current, WINDOW, now)).isTrue();
    }

    @Test
    void persistsOnNullTransition() {
        // Aircraft landed: altitude was reported, now "ground" (null)
        FlightEvent previous = event(40.0, -75.0, 1500.0, 120.0, 90.0, now.minus(Duration.ofMinutes(1)));
        AdsbAircraft current = report(40.0, -75.0, "ground", 0.0, null);
        assertThat(decider.shouldPersist(previous, current, WINDOW, now)).isTrue();
    }
}
