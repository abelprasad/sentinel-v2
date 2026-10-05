package com.sentinel.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.sentinel.ingestion.FlightEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * BaselineService against a real Postgres (Testcontainers).
 * Verifies Welford's algorithm converges to the true mean/variance,
 * per-dimension counts stay correct with null fields, and windows reset.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BaselineServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private BaselineService baselineService;

    @Autowired
    private BaselineRepository baselineRepository;

    @Autowired
    private com.sentinel.ingestion.AircraftRepository aircraftRepository;

    private com.sentinel.ingestion.Aircraft aircraft(long n) {
        return aircraftRepository.save(new com.sentinel.ingestion.Aircraft("test" + n));
    }

    private FlightEvent event(long aircraftId, double alt, double speed, double heading, Instant at) {
        FlightEvent e = new FlightEvent(aircraftId, at);
        e.setAltitudeFt(alt);
        e.setSpeedKts(speed);
        e.setHeadingDeg(heading);
        e.setLat(40.0);
        e.setLon(-75.0);
        return e;
    }

    @Test
    void welfordConvergesToTrueMeanAndVariance() {
        Long id = aircraft(1).getId();
        Baseline b = baselineService.getOrCreate(id);
        Instant t = Instant.now();
        double[] alts = {10000, 10200, 9800, 10100, 9900};
        for (double alt : alts) {
            baselineService.observe(b, event(id, alt, 400, 90, t), 10);
        }
        // True mean = 10000, sample variance = 25000, std = 158.11
        assertThat(b.getAvgAltitudeFt()).isCloseTo(10000.0, within(0.01));
        assertThat(b.getStdAltitudeFt()).isCloseTo(158.11, within(0.1));
        assertThat(b.getAltitudeCount()).isEqualTo(5);
        assertThat(b.getEventCount()).isEqualTo(5);
    }

    @Test
    void nullFieldsDontCorruptPerDimensionCounts() {
        Long id = aircraft(2).getId();
        Baseline b = baselineService.getOrCreate(id);
        Instant t = Instant.now();
        // 3 events with altitude, 5 with speed
        for (int i = 0; i < 3; i++) {
            FlightEvent e = event(id, 10000, 400, 90, t);
            baselineService.observe(b, e, 10);
        }
        for (int i = 0; i < 2; i++) {
            FlightEvent e = new FlightEvent(id, t);
            e.setSpeedKts(400.0);
            e.setLat(40.0);
            e.setLon(-75.0);
            baselineService.observe(b, e, 10);
        }
        assertThat(b.getAltitudeCount()).isEqualTo(3);
        assertThat(b.getSpeedCount()).isEqualTo(5);
        assertThat(b.getEventCount()).isEqualTo(5);
        // Altitude mean over 3 samples, not diluted by 5
        assertThat(b.getAvgAltitudeFt()).isCloseTo(10000.0, within(0.01));
    }

    @Test
    void circularHeadingMean() {
        Long id = aircraft(3).getId();
        Baseline b = baselineService.getOrCreate(id);
        Instant t = Instant.now();
        // Headings straddling 0°: mean should be ~0°, not ~180°
        for (double h : new double[]{358, 359, 0, 1, 2}) {
            baselineService.observe(b, event(id, 10000, 400, h, t), 10);
        }
        assertThat(b.getAvgHeadingDeg()).isCloseTo(0.0, within(2.0));
        assertThat(b.getHeadingCount()).isEqualTo(5);
    }

    @Test
    void windowExpiryResets() {
        Long id = aircraft(4).getId();
        Baseline b = baselineService.getOrCreate(id);
        Instant t0 = Instant.now();
        baselineService.observe(b, event(id, 10000, 400, 90, t0), 10);
        assertThat(b.getEventCount()).isEqualTo(1);

        // Event 7 hours later (window is 6h in test... default config) triggers reset
        FlightEvent late = event(4L, 20000, 400, 90, t0.plusSeconds(7 * 3600));
        baselineService.observe(b, late, 10);
        assertThat(b.getEventCount()).isEqualTo(1);
        assertThat(b.getAvgAltitudeFt()).isCloseTo(20000.0, within(0.01));
    }

    @Test
    void readinessGate() {
        Long id = aircraft(5).getId();
        Baseline b = baselineService.getOrCreate(id);
        Instant t = Instant.now();
        // minEventsForBaseline=10 in test profile... check config default
        boolean ready = false;
        for (int i = 0; i < 10; i++) {
            ready = baselineService.observe(b, event(id, 10000, 400, 90, t), 10);
        }
        assertThat(ready).isTrue();
        assertThat(b.getEventCount()).isEqualTo(10);
    }
}
