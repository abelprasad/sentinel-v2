package com.sentinel.ingestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the ADS-B feed on a fixed delay and persists the snapshot.
 *
 * <p>Batch-oriented: one lookup for all known aircraft, one insert
 * batch for new aircraft, one insert batch for events. v1 issued a
 * per-aircraft findByIcaoHex plus a save per poll (N+1).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final AdsbClient adsbClient;
    private final AircraftRepository aircraftRepository;
    private final FlightEventRepository eventRepository;

    public IngestionService(
            AdsbClient adsbClient,
            AircraftRepository aircraftRepository,
            FlightEventRepository eventRepository) {
        this.adsbClient = adsbClient;
        this.aircraftRepository = aircraftRepository;
        this.eventRepository = eventRepository;
    }

    @Scheduled(fixedDelayString = "${sentinel.adsb.poll-interval-ms}")
    @Transactional
    public void ingest() {
        List<AdsbAircraft> reports = adsbClient.fetch();
        if (reports.isEmpty()) {
            log.debug("ADS-B poll returned no aircraft");
            return;
        }
        ingestReports(reports, Instant.now());
    }

    /**
     * Persist one poll batch. Package-private so tests can drive it
     * without waiting on the scheduler.
     */
    void ingestReports(List<AdsbAircraft> reports, Instant recordedAt) {
        Map<String, Aircraft> known = aircraftRepository
                .findByIcaoHexIn(reports.stream().map(AdsbAircraft::hex).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Aircraft::getIcaoHex, Function.identity()));

        List<Aircraft> fresh = new ArrayList<>();
        for (AdsbAircraft report : reports) {
            Aircraft aircraft = known.get(report.hex());
            if (aircraft == null) {
                aircraft = new Aircraft(report.hex());
                aircraft.setCallsign(report.callsign());
                aircraft.setCategory(report.category());
                fresh.add(aircraft);
                known.put(report.hex(), aircraft);
            } else {
                if (report.callsign() != null) {
                    aircraft.setCallsign(report.callsign());
                }
                aircraft.touch();
            }
        }
        if (!fresh.isEmpty()) {
            aircraftRepository.saveAll(fresh);
            log.info("Registered {} new aircraft", fresh.size());
        }

        List<FlightEvent> events = new ArrayList<>(reports.size());
        for (AdsbAircraft report : reports) {
            Aircraft aircraft = known.get(report.hex());
            FlightEvent event = new FlightEvent(aircraft.getId(), recordedAt);
            event.setAltitudeFt(report.altitudeFt());
            event.setSpeedKts(report.groundSpeedKts());
            event.setHeadingDeg(report.trackDeg());
            event.setLat(report.lat());
            event.setLon(report.lon());
            events.add(event);
        }
        eventRepository.saveAll(events);
        log.info("ADS-B ingestion complete - {} events for {} aircraft", events.size(), known.size());
    }
}
