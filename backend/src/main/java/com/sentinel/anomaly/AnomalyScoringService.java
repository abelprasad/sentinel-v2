package com.sentinel.anomaly;

import com.sentinel.config.SentinelProperties;
import com.sentinel.ingestion.FlightEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scores individual flight events against rolling baselines.
 *
 * <p>Pipeline per event:
 * <ol>
 *   <li>Fold the event into the aircraft's baseline (Welford update).</li>
 *   <li>Skip scoring until the baseline has enough samples —
 *       scoring against 2 data points is noise.</li>
 *   <li>Compute per-dimension z-scores; the composite is the max.</li>
 *   <li>Apply cooldown: suppress repeat flags inside the window, unless
 *       the score escalates (genuinely worse than the parent anomaly).</li>
 *   <li>Flag with a rule-based explanation naming the offending dimensions.</li>
 * </ol>
 */
@Service
public class AnomalyScoringService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyScoringService.class);

    private final BaselineService baselineService;
    private final ZScoreCalculator calculator;
    private final AnomalyRepository anomalyRepository;
    private final double threshold;
    private final int minEventsForBaseline;
    private final int baselineWindowHours;
    private final Duration cooldown;
    private final double escalationMultiplier;

    public AnomalyScoringService(
            BaselineService baselineService,
            ZScoreCalculator calculator,
            AnomalyRepository anomalyRepository,
            SentinelProperties properties) {
        this.baselineService = baselineService;
        this.calculator = calculator;
        this.anomalyRepository = anomalyRepository;
        this.threshold = properties.anomaly().threshold();
        this.minEventsForBaseline = properties.anomaly().minEventsForBaseline();
        this.baselineWindowHours = properties.anomaly().baselineWindowHours();
        this.cooldown = Duration.ofMinutes(properties.anomaly().cooldownMinutes());
        this.escalationMultiplier = properties.anomaly().escalationMultiplier();
    }

    /**
     * Score one event. Updates the baseline whether or not an anomaly
     * is flagged — the baseline must keep learning through anomalies,
     * or a sustained deviation permanently poisons the comparison.
     *
     * @return the persisted anomaly, or empty when the event is nominal
     */
    @Transactional
    public Optional<Anomaly> score(FlightEvent event) {
        Baseline baseline = baselineService.getOrCreate(event.getAircraftId());
        boolean ready = baselineService.observe(baseline, event, minEventsForBaseline);
        if (!ready) {
            log.debug("Baseline not ready for aircraft {} ({}/{} samples)",
                    event.getAircraftId(), baseline.getEventCount(), minEventsForBaseline);
            return Optional.empty();
        }

        ZScoreCalculator.ZScores z = calculator.calculate(event, baseline);
        Double max = z.max();
        if (max == null || max < threshold) {
            return Optional.empty();
        }

        Optional<Anomaly> coolingDownFrom = findCooldownParent(event.getAircraftId());
        Long parentId = null;
        if (coolingDownFrom.isPresent()) {
            Anomaly parent = coolingDownFrom.get();
            if (max >= parent.getScore() * escalationMultiplier) {
                // Genuinely worse — link as an escalation instead of suppressing.
                parentId = parent.getId();
                log.info("Escalating anomaly {} for aircraft {}: {} -> {}",
                        parent.getId(), event.getAircraftId(),
                        String.format("%.2f", parent.getScore()), String.format("%.2f", max));
            } else {
                log.debug("Aircraft {} in cooldown, suppressing score={}",
                        event.getAircraftId(), String.format("%.2f", max));
                return Optional.empty();
            }
        }

        Anomaly anomaly = new Anomaly(event.getAircraftId(), event.getId(), max);
        anomaly.setParentAnomalyId(parentId);
        anomaly.setZAltitude(z.altitude());
        anomaly.setZSpeed(z.speed());
        anomaly.setZHeading(z.heading());
        anomaly.setZPosition(z.position());
        anomaly.setExplanation(buildExplanation(event, baseline, z));
        anomaly.setExplanationSrc(Anomaly.SRC_RULE);

        Anomaly saved = anomalyRepository.save(anomaly);
        log.info("Flagged anomaly {} for aircraft {}: score={} ({})",
                saved.getId(), event.getAircraftId(), String.format("%.2f", max), z.dominantDimension());
        return Optional.of(saved);
    }

    /**
     * v1 flagged 142 anomalies/hour because every deviant poll became a
     * standalone alert. The cooldown suppresses repeat flags for one
     * aircraft until the window lapses; a significantly worse score
     * escalates instead (linked via parentAnomalyId).
     *
     * @return the recent anomaly causing the cooldown, or empty when clear
     */
    private Optional<Anomaly> findCooldownParent(Long aircraftId) {
        return anomalyRepository.findFirstByAircraftIdOrderByFlaggedAtDesc(aircraftId)
                .filter(last -> Duration.between(last.getFlaggedAt(), Instant.now()).compareTo(cooldown) < 0);
    }

    private String buildExplanation(FlightEvent event, Baseline baseline, ZScoreCalculator.ZScores z) {
        List<String> parts = new ArrayList<>();
        if (z.altitude() != null && z.altitude() >= threshold) {
            parts.add(String.format("altitude %.1fσ (%,.0fft vs %,.0fft avg)",
                    z.altitude(), event.getAltitudeFt(), baseline.getAvgAltitudeFt()));
        }
        if (z.speed() != null && z.speed() >= threshold) {
            parts.add(String.format("speed %.1fσ (%.0fkts vs %.0fkts avg)",
                    z.speed(), event.getSpeedKts(), baseline.getAvgSpeedKts()));
        }
        if (z.heading() != null && z.heading() >= threshold) {
            parts.add(String.format("heading %.1fσ (%.0f° vs %.0f° avg)",
                    z.heading(), event.getHeadingDeg(), baseline.getAvgHeadingDeg()));
        }
        if (z.position() != null && z.position() >= threshold) {
            parts.add(String.format("position %.1fσ off baseline track", z.position()));
        }
        return "Deviation from %dh baseline: %s".formatted(baselineWindowHours, String.join("; ", parts));
    }
}
