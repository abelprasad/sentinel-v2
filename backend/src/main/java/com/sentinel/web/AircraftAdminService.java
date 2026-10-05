package com.sentinel.web;

import com.sentinel.anomaly.AnomalyRepository;
import com.sentinel.anomaly.BaselineRepository;
import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.ingestion.FlightEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin mutations on aircraft. Deletion cascades to events, baselines,
 * and anomalies in one transaction — the schema uses raw FK columns
 * (no JPA cascades), so the service owns the cleanup.
 */
@Service
public class AircraftAdminService {

    private final AircraftRepository aircraftRepository;
    private final FlightEventRepository eventRepository;
    private final BaselineRepository baselineRepository;
    private final AnomalyRepository anomalyRepository;

    public AircraftAdminService(
            AircraftRepository aircraftRepository,
            FlightEventRepository eventRepository,
            BaselineRepository baselineRepository,
            AnomalyRepository anomalyRepository) {
        this.aircraftRepository = aircraftRepository;
        this.eventRepository = eventRepository;
        this.baselineRepository = baselineRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional
    public void deleteAircraft(Long id) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("aircraft", String.valueOf(id)));
        Long aircraftId = aircraft.getId();
        anomalyRepository.deleteByAircraftId(aircraftId);
        baselineRepository.deleteByAircraftId(aircraftId);
        eventRepository.deleteByAircraftId(aircraftId);
        aircraftRepository.delete(aircraft);
    }
}
