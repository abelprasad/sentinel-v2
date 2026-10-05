package com.sentinel.ingestion;

import com.sentinel.config.SentinelProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * per-aircraft findByIcaoHex plus a save per poll (N+1), and wrote
 * every report unconditionally — see {@link DedupDecider}.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final AdsbClient adsbClient;
    private final AircraftRepository aircraftRepository;
    private final FlightEventRepository eventRepository;
    private final DedupDecider dedupDecider;
    private final SentinelProperties props;

    /**
     * Latest persisted event per aircraft. Lets the dedup check run
     * without a DB read per aircraft per poll; falls back to the
     * database on a cold start.
     */
    private final Map<Long, FlightEvent> lastEventCache = new ConcurrentHashMap<>();

    public IngestionService(
            AdsbClient adsbClient,
            AircraftRepository aircraftRepository,
            FlightEventRepository eventRepository,
            DedupDecider dedupDecider,
            SentinelProperties props) {
        this.adsbClient = adsbClient;
        this.aircraftRepository = aircraftRepository;
        this.eventRepository = eventRepository;
        this.dedupDecider = dedupDecider;
        this.props = props;
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

        Duration window = Duration.ofMinutes(props.anomaly().dedupMinutes());
        List<FlightEvent> events = new ArrayList<>(reports.size());
        int skipped = 0;
        for (AdsbAircraft report : reports) {
            Aircraft aircraft = known.get(report.hex());
            FlightEvent previous = lastEventFor(aircraft.getId());
            if (!dedupDecider.shouldPersist(previous, report, window, recordedAt)) {
                skipped++;
                continue;
            }
            FlightEvent event = new FlightEvent(aircraft.getId(), recordedAt);
            event.setAltitudeFt(report.altitudeFt());
            event.setSpeedKts(report.groundSpeedKts());
            event.setHeadingDeg(report.trackDeg());
            event.setLat(report.lat());
            event.setLon(report.lon());
            events.add(event);
            lastEventCache.put(aircraft.getId(), event);
        }
        if (!events.isEmpty()) {
            eventRepository.saveAll(events);
        }
        log.info("ADS-B ingestion complete - {} events, {} deduped, {} aircraft",
                events.size(), skipped, known.size());
    }

    private FlightEvent lastEventFor(Long aircraftId) {
        FlightEvent cached = lastEventCache.get(aircraftId);
        if (cached != null) {
            return cached;
        }
        return eventRepository.findFirstByAircraftIdOrderByRecordedAtDesc(aircraftId)
                .map(e -> {
                    lastEventCache.put(aircraftId, e);
                    return e;
                })
                .orElse(null);
    }
}
