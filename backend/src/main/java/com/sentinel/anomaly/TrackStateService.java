package com.sentinel.anomaly;

import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the track lifecycle state machine.
 *
 * <pre>
 *   NEW ──(baseline ready)──▶ ACTIVE ──(10m silent)──▶ STALE ──(60m silent)──▶ LOST
 *    ▲                          │                         │
 *    └──────(seen again)────────┴────────(seen again)──────┘
 * </pre>
 *
 * <p>States drive the UI: NEW tracks are still learning (no scoring yet),
 * ACTIVE is the normal tracked population, STALE is "haven't heard from
 * in a while" (left the receiver's range or landed), LOST is "gone"
 * (candidates for pruning). Any fresh event promotes back to ACTIVE —
 * the ingestion service calls {@link #markActive} on every poll hit.
 *
 * <p>A scheduled sweep demotes silent tracks; it runs every minute and
 * touches only rows whose state actually changes.
 */
@Service
public class TrackStateService {

    private static final Logger log = LoggerFactory.getLogger(TrackStateService.class);

    /** Silence after which ACTIVE becomes STALE. */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    /** Silence after which STALE becomes LOST. */
    static final Duration LOST_AFTER = Duration.ofMinutes(60);

    private final AircraftRepository aircraftRepository;
    private final BaselineRepository baselineRepository;

    public TrackStateService(AircraftRepository aircraftRepository, BaselineRepository baselineRepository) {
        this.aircraftRepository = aircraftRepository;
        this.baselineRepository = baselineRepository;
    }

    /**
     * Promote a track on fresh telemetry. NEW becomes ACTIVE once its
     * baseline is ready; STALE/LOST always recover to ACTIVE on contact.
     */
    @Transactional
    public void markActive(Aircraft aircraft, boolean baselineReady) {        String current = aircraft.getTrackState();
        String next = null;
        if ("STALE".equals(current) || "LOST".equals(current)) {
            next = TrackState.ACTIVE.name();
        } else if ("NEW".equals(current) && baselineReady) {
            next = TrackState.ACTIVE.name();
        }
        if (next != null) {
            aircraft.setTrackState(next);
            aircraftRepository.save(aircraft);
            log.debug("Track {} {} -> {}", aircraft.getIcaoHex(), current, next);
        }
    }

    /**
     * Promote by ID: loads the aircraft, checks baseline readiness, delegates.
     * Used by the engine, which works with event aircraft IDs.
     */
    @Transactional
    public void markActiveById(Long aircraftId, int minEventsForBaseline) {
        aircraftRepository.findById(aircraftId).ifPresent(aircraft -> {
            boolean ready = baselineRepository.findByAircraftId(aircraftId)
                    .map(b -> b.getEventCount() >= minEventsForBaseline)
                    .orElse(false);
            markActive(aircraft, ready);
        });
    }

    /** Every minute: demote tracks that have gone silent. */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        Instant staleCutoff = now.minus(STALE_AFTER);
        Instant lostCutoff = now.minus(LOST_AFTER);

        List<Aircraft> actives = aircraftRepository.findByTrackState(TrackState.ACTIVE.name());
        int toStale = 0;
        int toLost = 0;
        for (Aircraft aircraft : actives) {
            if (aircraft.getLastSeen().isBefore(lostCutoff)) {
                aircraft.setTrackState(TrackState.LOST.name());
                toLost++;
            } else if (aircraft.getLastSeen().isBefore(staleCutoff)) {
                aircraft.setTrackState(TrackState.STALE.name());
                toStale++;
            }
        }
        List<Aircraft> stales = aircraftRepository.findByTrackState(TrackState.STALE.name());
        for (Aircraft aircraft : stales) {
            if (aircraft.getLastSeen().isBefore(lostCutoff)) {
                aircraft.setTrackState(TrackState.LOST.name());
                toLost++;
            }
        }
        if (toStale + toLost > 0) {
            aircraftRepository.saveAll(actives);
            aircraftRepository.saveAll(stales);
            log.info("Track sweep: {} -> STALE, {} -> LOST", toStale, toLost);
        }
    }

    /** Track lifecycle states. */
    public enum TrackState {
        /** Seen but baseline not yet ready — not scored. */
        NEW,
        /** Baseline ready and recently seen — the normal tracked population. */
        ACTIVE,
        /** No telemetry for 10 minutes — likely out of range or landed. */
        STALE,
        /** No telemetry for 60 minutes — gone, candidate for pruning. */
        LOST
    }
}
