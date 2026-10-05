package com.sentinel.anomaly;

import com.sentinel.config.SentinelProperties;
import com.sentinel.ingestion.FlightEvent;
import com.sentinel.ingestion.FlightEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled anomaly detection pipeline.
 *
 * <p>Runs after each ingestion poll: picks up every {@link FlightEvent}
 * since the last run (ID watermark, so restarts only rescore a small
 * overlap), folds each into its baseline, and persists flags for
 * threshold breaches.
 *
 * <p>The watermark starts at the current max event ID on boot — the
 * engine scores only fresh telemetry, never backfills history. Backfill
 * is a separate operational concern (replay mode covers it).
 */
@Service
public class AnomalyEngine {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEngine.class);

    private final FlightEventRepository eventRepository;
    private final AnomalyScoringService scoringService;
    private final TrackStateService trackStateService;
    private final int minEventsForBaseline;
    private final AtomicLong watermark = new AtomicLong(-1);

    public AnomalyEngine(FlightEventRepository eventRepository, AnomalyScoringService scoringService,
            TrackStateService trackStateService, SentinelProperties properties) {
        this.eventRepository = eventRepository;
        this.scoringService = scoringService;
        this.trackStateService = trackStateService;
        this.minEventsForBaseline = properties.anomaly().minEventsForBaseline();
    }

    /** Start the watermark at the latest event so boot never rescores history. */
    @jakarta.annotation.PostConstruct
    void initWatermark() {
        eventRepository.findFirstByOrderByIdDesc()
                .ifPresentOrElse(
                        e -> watermark.set(e.getId()),
                        () -> watermark.set(0));
        log.info("Anomaly engine watermark initialized at event id {}", watermark.get());
    }

    @Scheduled(fixedDelayString = "${sentinel.adsb.poll-interval-ms:30000}")
    public void run() {
        long from = watermark.get();
        List<FlightEvent> events = eventRepository.findByIdGreaterThanOrderByIdAsc(from);
        if (events.isEmpty()) {
            return;
        }

        int flagged = 0;
        long maxId = from;
        for (FlightEvent event : events) {
            try {
                Optional<Anomaly> anomaly = scoringService.score(event);
                if (anomaly.isPresent()) {
                    flagged++;
                }
                trackStateService.markActiveById(event.getAircraftId(), minEventsForBaseline);
            } catch (Exception e) {
                // One bad event must not kill the batch or stall the watermark.
                log.warn("Scoring failed for event {}, skipping", event.getId(), e);
            }
            maxId = Math.max(maxId, event.getId());
        }
        watermark.set(maxId);

        if (flagged > 0) {
            log.info("Anomaly engine scored {} events, flagged {}", events.size(), flagged);
        } else {
            log.debug("Anomaly engine scored {} events, none flagged", events.size());
        }
    }

    /** Visible for tests. */
    long getWatermark() {
        return watermark.get();
    }
}
