package com.sentinel.web;

import com.sentinel.anomaly.AnomalyRepository;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.ingestion.FlightEventRepository;
import com.sentinel.web.dto.StatusDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public system status. No auth — this powers the public demo landing
 * state and external uptime monitors. Aggregate counters only; nothing
 * sensitive.
 */
@RestController
@RequestMapping("/api/public/status")
public class PublicStatusController {

    private final AircraftRepository aircraftRepository;
    private final FlightEventRepository eventRepository;
    private final AnomalyRepository anomalyRepository;

    public PublicStatusController(
            AircraftRepository aircraftRepository,
            FlightEventRepository eventRepository,
            AnomalyRepository anomalyRepository) {
        this.aircraftRepository = aircraftRepository;
        this.eventRepository = eventRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @GetMapping
    public StatusDto status() {
        Instant now = Instant.now();
        return new StatusDto(
                "UP",
                "sentinel-backend",
                now,
                aircraftRepository.count(),
                aircraftRepository.findByTrackState("ACTIVE").size(),
                eventRepository.countByRecordedAtAfter(now.minus(1, ChronoUnit.HOURS)),
                anomalyRepository.countByAcknowledgedFalse(),
                anomalyRepository.countByFlaggedAtAfter(now.minus(24, ChronoUnit.HOURS)));
    }
}
