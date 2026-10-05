package com.sentinel.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.ingestion.FlightEvent;
import com.sentinel.ingestion.FlightEventRepository;
import java.time.Instant;
import java.util.Optional;
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
 * Scoring pipeline against a real Postgres (Testcontainers).
 * Verifies the readiness gate, threshold flagging, cooldown suppression,
 * and escalation linking.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnomalyScoringServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private AnomalyScoringService scoringService;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private FlightEventRepository eventRepository;

    @Autowired
    private BaselineRepository baselineRepository;

    private Aircraft aircraft(String hex) {
        Aircraft a = new Aircraft(hex);
        return aircraftRepository.save(a);
    }

    private FlightEvent persistEvent(Aircraft a, double alt, double speed, double heading) {
        FlightEvent e = new FlightEvent(a.getId(), Instant.now());
        e.setAltitudeFt(alt);
        e.setSpeedKts(speed);
        e.setHeadingDeg(heading);
        e.setLat(40.0);
        e.setLon(-75.0);
        return eventRepository.save(e);
    }

    /** Feed enough nominal events to ready the baseline. */
    private void warmBaseline(Aircraft a) {
        for (int i = 0; i < 10; i++) {
            Optional<Anomaly> anomaly = scoringService.score(persistEvent(a, 10000, 400, 90));
            assertThat(anomaly).isEmpty(); // nominal — nothing flagged during warmup
        }
    }

    @Test
    void flagsGenuineDeviation() {
        Aircraft a = aircraft("a1b2c3");
        warmBaseline(a);

        // Sudden climb: 5000ft above baseline
        Optional<Anomaly> result = scoringService.score(persistEvent(a, 15000, 400, 90));
        assertThat(result).isPresent();
        Anomaly anomaly = result.get();
        assertThat(anomaly.getScore()).isGreaterThanOrEqualTo(3.0);
        assertThat(anomaly.getZAltitude()).isNotNull();
        assertThat(anomaly.getExplanation()).contains("altitude");
        assertThat(anomaly.getExplanationSrc()).isEqualTo(Anomaly.SRC_RULE);
        assertThat(anomaly.getParentAnomalyId()).isNull(); // standalone
    }

    @Test
    void cooldownSuppressesRepeatFlags() {
        Aircraft a = aircraft("d4e5f6");
        warmBaseline(a);

        Optional<Anomaly> first = scoringService.score(persistEvent(a, 15000, 400, 90));
        assertThat(first).isPresent();

        // Same deviation immediately after — inside cooldown, suppressed
        Optional<Anomaly> second = scoringService.score(persistEvent(a, 15000, 400, 90));
        assertThat(second).isEmpty();

        assertThat(anomalyRepository.count()).isEqualTo(1);
    }

    @Test
    void escalationLinksWhenGenuinelyWorse() {
        Aircraft a = aircraft("e5f6a7");

        // Pre-seed a loose baseline (std 1000ft over 100 samples) so a big
        // deviation scores high without the single-outlier cap (~3.0) that
        // a tight warmup baseline imposes via Welford.
        Baseline b = new Baseline(a.getId());
        b.setAvgAltitudeFt(10000.0);
        b.setM2AltitudeFt(1000.0 * 1000.0 * 99);
        b.setAltitudeCount(100);
        b.setAvgSpeedKts(400.0);
        b.setM2SpeedKts(20.0 * 20.0 * 99);
        b.setSpeedCount(100);
        b.setAvgHeadingDeg(90.0);
        b.setSumSinHeading(Math.sin(Math.toRadians(90)) * 100);
        b.setSumCosHeading(Math.cos(Math.toRadians(90)) * 100);
        b.setHeadingCount(100);
        b.setAvgLat(40.0);
        b.setAvgLon(-75.0);
        b.setPositionCount(100);
        b.setEventCount(100);
        b.setWindowStart(Instant.now());
        baselineRepository.save(b);

        // Seed a parent anomaly (score 3.0, flagged now) to trigger cooldown.
        FlightEvent parentEvent = persistEvent(a, 15000, 400, 90);
        Anomaly parent = new Anomaly(a.getId(), parentEvent.getId(), 3.0);
        parent.setExplanation("test parent");
        parent = anomalyRepository.save(parent);

        // 6000ft deviation against loose baseline scores ~5.1 >= 3.0*1.5 → escalates.
        Optional<Anomaly> escalated = scoringService.score(persistEvent(a, 16000, 400, 90));
        assertThat(escalated).isPresent();
        assertThat(escalated.get().getParentAnomalyId()).isEqualTo(parent.getId());
        assertThat(escalated.get().getScore()).isGreaterThan(parent.getScore());
    }

    @Test
    void nominalEventsNeverFlag() {
        Aircraft a = aircraft("b8c9d0");
        for (int i = 0; i < 20; i++) {
            // Small jitter around baseline — within floors
            Optional<Anomaly> result = scoringService.score(persistEvent(a, 10000 + (i % 3) * 50, 400, 90));
            assertThat(result).isEmpty();
        }
        assertThat(anomalyRepository.count()).isEqualTo(0);
    }
}
