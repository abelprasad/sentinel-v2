package com.sentinel.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
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
 * Track lifecycle state machine against a real Postgres.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrackStateServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private TrackStateService trackStateService;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private BaselineRepository baselineRepository;

    @Test
    void newTrackStaysNewUntilBaselineReady() {
        Aircraft a = aircraftRepository.save(new Aircraft("a1b2c3"));
        assertThat(a.getTrackState()).isEqualTo("NEW");

        trackStateService.markActive(a, false);
        assertThat(aircraftRepository.findById(a.getId()).orElseThrow().getTrackState())
                .isEqualTo("NEW");
    }

    @Test
    void newBecomesActiveWhenBaselineReady() {
        Aircraft a = aircraftRepository.save(new Aircraft("d4e5f6"));
        // Seed a ready baseline
        Baseline b = new Baseline(a.getId());
        b.setEventCount(10);
        baselineRepository.save(b);

        trackStateService.markActive(a, true);
        assertThat(aircraftRepository.findById(a.getId()).orElseThrow().getTrackState())
                .isEqualTo("ACTIVE");
    }

    @Test
    void staleRecoversToActiveOnContact() {
        Aircraft a = aircraftRepository.save(new Aircraft("e5f6a7"));
        a.setTrackState("STALE");
        aircraftRepository.save(a);

        trackStateService.markActiveById(a.getId(), 10);
        assertThat(aircraftRepository.findById(a.getId()).orElseThrow().getTrackState())
                .isEqualTo("ACTIVE");
    }

    @Test
    void sweepDemotesSilentTracks() throws Exception {
        Aircraft active = aircraftRepository.save(new Aircraft("b8c9d0"));
        active.setTrackState("ACTIVE");
        // Fake an old last_seen via reflection-free approach: save, then update with SQL age
        aircraftRepository.save(active);

        // Use a raw old timestamp by re-saving with manipulated entity
        Aircraft stale = aircraftRepository.save(new Aircraft("c9d0e1"));
        stale.setTrackState("ACTIVE");
        aircraftRepository.save(stale);
        // Simulate 11 minutes of silence by directly updating last_seen
        setLastSeen(stale.getId(), Instant.now().minusSeconds(11 * 60));

        Aircraft lost = aircraftRepository.save(new Aircraft("d0e1f2"));
        lost.setTrackState("STALE");
        aircraftRepository.save(lost);
        setLastSeen(lost.getId(), Instant.now().minusSeconds(61 * 60));

        trackStateService.sweep();

        assertThat(aircraftRepository.findById(active.getId()).orElseThrow().getTrackState())
                .isEqualTo("ACTIVE"); // recently seen — untouched
        assertThat(aircraftRepository.findById(stale.getId()).orElseThrow().getTrackState())
                .isEqualTo("STALE");
        assertThat(aircraftRepository.findById(lost.getId()).orElseThrow().getTrackState())
                .isEqualTo("LOST");
    }

    private void setLastSeen(Long id, Instant when) {
        // last_seen has no setter by design (touch() sets now); use a direct update
        aircraftRepository.findById(id).ifPresent(a -> {
            try {
                var field = Aircraft.class.getDeclaredField("lastSeen");
                field.setAccessible(true);
                field.set(a, when);
                aircraftRepository.save(a);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
