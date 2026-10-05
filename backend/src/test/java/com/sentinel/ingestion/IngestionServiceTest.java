package com.sentinel.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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
 * Ingestion slice against a real Postgres (Testcontainers).
 * Flyway runs the V1 migration; no H2, no mocks of the database.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IngestionServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private FlightEventRepository eventRepository;

    private static AdsbAircraft report(String hex, double lat, double lon) {
        return new AdsbAircraft(hex, "TST", lat, lon, 35000, 450.0, 90.0, "B738", "A3");
    }

    @Test
    void registersNewAircraftAndPersistsEvents() {
        Instant t0 = Instant.now();
        ingestionService.ingestReports(
                List.of(report("a1b2c3", 40.0, -75.0), report("d4e5f6", 40.1, -75.1)), t0);

        assertThat(aircraftRepository.findAll()).hasSize(2);
        assertThat(aircraftRepository.findByIcaoHex("a1b2c3")).isPresent();
        assertThat(eventRepository.findAll()).hasSize(2);
    }

    @Test
    void dedupesUnchangedSecondPoll() {
        Instant t0 = Instant.now();
        List<AdsbAircraft> batch = List.of(report("aa0001", 40.0, -75.0));

        ingestionService.ingestReports(batch, t0);
        // Same report 60s later, inside the 5-minute dedup window
        ingestionService.ingestReports(batch, t0.plusSeconds(60));

        assertThat(aircraftRepository.findByIcaoHex("aa0001")).isPresent();
        assertThat(eventRepository.findAll())
                .as("unchanged report inside dedup window should not create a second event")
                .hasSize(1);
    }

    @Test
    void persistsMovedAircraft() {
        Instant t0 = Instant.now();
        ingestionService.ingestReports(List.of(report("bb0002", 40.0, -75.0)), t0);
        // Moved ~2km north, 60s later
        ingestionService.ingestReports(List.of(report("bb0002", 40.02, -75.0)), t0.plusSeconds(60));

        assertThat(eventRepository.findAll()).hasSize(2);
    }

    @Test
    void persistsAfterDedupWindowExpires() {
        Instant t0 = Instant.now();
        List<AdsbAircraft> batch = List.of(report("cc0003", 40.0, -75.0));

        ingestionService.ingestReports(batch, t0);
        // Same report 6 minutes later — window (5 min) expired, heartbeat kept
        ingestionService.ingestReports(batch, t0.plusSeconds(360));

        assertThat(eventRepository.findAll()).hasSize(2);
    }
}
