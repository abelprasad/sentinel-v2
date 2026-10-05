package com.sentinel.web;

import static org.assertj.core.api.Assertions.assertThat;
import com.sentinel.security.JwtTokenService;
import com.sentinel.security.Role;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentinel.anomaly.Anomaly;
import com.sentinel.anomaly.AnomalyRepository;
import com.sentinel.anomaly.Baseline;
import com.sentinel.anomaly.BaselineRepository;
import com.sentinel.ingestion.AdsbClient;
import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.ingestion.FlightEvent;
import com.sentinel.ingestion.FlightEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * API slice: public endpoints are open, admin endpoints require auth,
 * and missing resources are 404 (never 403 — the v1 bug).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokens;

    /** Mints a real JWT for tests — no HTTP basic anymore. */
    private String adminToken() {
        return "Bearer " + tokens.generate("test-admin", Role.ADMIN);
    }

    @Autowired
    private AircraftRepository aircraftRepository;

    @Autowired
    private FlightEventRepository eventRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private BaselineRepository baselineRepository;

    /**
     * The ingestion scheduler runs on its own thread outside the test
     * transaction — if it fires mid-test it would hit the real ADS-B API
     * and commit rows the test cannot roll back. Mocking the client makes
     * the scheduler a no-op.
     */
    @MockBean
    private AdsbClient adsbClient;

    private Aircraft aircraft;

    @BeforeEach
    void seed() {
        when(adsbClient.fetch()).thenReturn(java.util.List.of());

        // Defensive: the shared container can retain rows if a prior run
        // failed mid-rollback. Start every test from a known empty state.
        anomalyRepository.deleteAll();
        baselineRepository.deleteAll();
        eventRepository.deleteAll();
        aircraftRepository.deleteAll();

        aircraft = aircraftRepository.save(new Aircraft("a1b2c3"));
        aircraft.setCallsign("TST123");
        aircraft = aircraftRepository.save(aircraft);

        FlightEvent event = new FlightEvent(aircraft.getId(), Instant.now());
        event.setLat(40.0);
        event.setLon(-75.0);
        event.setAltitudeFt(35000.0);
        eventRepository.save(event);

        Anomaly anomaly = new Anomaly(aircraft.getId(), event.getId(), 4.2);
        anomaly.setExplanation("test flag");
        anomalyRepository.save(anomaly);

        baselineRepository.save(new Baseline(aircraft.getId()));
    }

    // --- Public endpoints: no auth required ---

    @Test
    void publicStatusIsOpen() throws Exception {
        mockMvc.perform(get("/api/public/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.aircraftTracked").value(1));
    }

    @Test
    void publicTrackReturnsAircraftAndPoints() throws Exception {
        mockMvc.perform(get("/api/public/tracks/a1b2c3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aircraft.icaoHex").value("a1b2c3"))
                .andExpect(jsonPath("$.aircraft.callsign").value("TST123"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points.length()").value(1));
    }

    @Test
    void missingTrackIs404Not403() throws Exception {
        // v1 returned 403 here because authz ran before the existence check.
        mockMvc.perform(get("/api/public/tracks/deadbe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void publicAnomaliesAreOpenAndPaginated() throws Exception {
        mockMvc.perform(get("/api/public/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].icaoHex").value("a1b2c3"));
    }

    @Test
    void publicAnomaliesFilterByIcaoHex() throws Exception {
        mockMvc.perform(get("/api/public/anomalies").param("icaoHex", "a1b2c3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/public/anomalies").param("icaoHex", "nope01"))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsOpen() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // --- Admin endpoints: auth required ---

    @Test
    void adminEndpointsRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/aircraft")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/anomalies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/baselines")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminAircraftCrud() throws Exception {
        // list
        mockMvc.perform(get("/api/admin/aircraft").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // get one
        mockMvc.perform(get("/api/admin/aircraft/" + aircraft.getId()).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icaoHex").value("a1b2c3"));

        // get missing -> 404
        mockMvc.perform(get("/api/admin/aircraft/999999").header("Authorization", adminToken()))
                .andExpect(status().isNotFound());

        // patch callsign
        mockMvc.perform(patch("/api/admin/aircraft/" + aircraft.getId())
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callsign\": \"NEW999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callsign").value("NEW999"));

        // patch validation: callsign too long -> 400
        mockMvc.perform(patch("/api/admin/aircraft/" + aircraft.getId())
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callsign\": \"THIS_CALLSIGN_IS_WAY_TOO_LONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        // delete cascades
        mockMvc.perform(delete("/api/admin/aircraft/" + aircraft.getId()).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        assertThat(aircraftRepository.findById(aircraft.getId())).isEmpty();
        assertThat(eventRepository.findAll()).isEmpty();
        assertThat(anomalyRepository.findAll()).isEmpty();
        assertThat(baselineRepository.findAll()).isEmpty();
    }

    @Test
    void adminAnomalyWorkflow() throws Exception {
        Long anomalyId = anomalyRepository.findAll().get(0).getId();

        // acknowledge
        mockMvc.perform(post("/api/admin/anomalies/" + anomalyId + "/acknowledge")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(true));

        // escalate then de-escalate
        mockMvc.perform(post("/api/admin/anomalies/" + anomalyId + "/escalate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalated").value(true));

        mockMvc.perform(delete("/api/admin/anomalies/" + anomalyId + "/escalate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalated").value(false));

        // acknowledged filter
        mockMvc.perform(get("/api/admin/anomalies")
                        .param("acknowledged", "true")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // missing anomaly -> 404
        mockMvc.perform(post("/api/admin/anomalies/999999/acknowledge").header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminBaselineViewAndReset() throws Exception {
        // get baseline
        mockMvc.perform(get("/api/admin/baselines/" + aircraft.getId()).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icaoHex").value("a1b2c3"));

        // reset
        mockMvc.perform(post("/api/admin/baselines/" + aircraft.getId() + "/reset")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.altitudeCount").value(0));

        // unknown aircraft -> 404
        mockMvc.perform(get("/api/admin/baselines/999999").header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        mockMvc.perform(get("/api/nope").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
    }
}
