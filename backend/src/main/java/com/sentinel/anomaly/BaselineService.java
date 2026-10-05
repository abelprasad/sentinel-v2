package com.sentinel.anomaly;

import com.sentinel.config.SentinelProperties;
import com.sentinel.ingestion.FlightEvent;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains rolling per-aircraft baselines with Welford's online algorithm.
 *
 * <p>Each new {@link FlightEvent} updates the baseline in O(1):
 * for linear dimensions (altitude, speed) we keep a running mean and M2;
 * for heading (circular) we keep the sum of sin/cos. Statistics cover a
 * sliding window ({@code sentinel.anomaly.baseline-window-hours}); when an
 * event arrives past the window edge, the state resets and a new window
 * begins. A hard reset is a deliberate v1 tradeoff — simple, explainable,
 * and resets are rare on multi-hour windows.
 *
 * <p>Null telemetry fields are skipped per-dimension rather than dropping
 * the whole event: an aircraft reporting position but no altitude still
 * contributes to the speed/heading/position statistics.
 */
@Service
public class BaselineService {

    private static final Logger log = LoggerFactory.getLogger(BaselineService.class);

    private final BaselineRepository baselineRepository;
    private final Duration windowLength;

    public BaselineService(BaselineRepository baselineRepository, SentinelProperties properties) {
        this.baselineRepository = baselineRepository;
        this.windowLength = Duration.ofHours(properties.anomaly().baselineWindowHours());
    }

    /** Load the baseline, creating an empty one when the aircraft has never been seen. */
    @Transactional
    public Baseline getOrCreate(Long aircraftId) {
        return baselineRepository.findByAircraftId(aircraftId)
                .orElseGet(() -> baselineRepository.save(new Baseline(aircraftId)));
    }

    /**
     * Fold one event into the baseline.
     *
     * @return true when the baseline now has enough samples to score against
     *         ({@code sentinel.anomaly.min-events-for-baseline})
     */
    @Transactional
    public boolean observe(Baseline baseline, FlightEvent event, int minEventsForBaseline) {
        Instant recordedAt = event.getRecordedAt() != null ? event.getRecordedAt() : Instant.now();

        if (windowExpired(baseline, recordedAt)) {
            log.debug("Baseline window expired for aircraft {}, resetting", baseline.getAircraftId());
            reset(baseline, recordedAt);
        }
        if (baseline.getWindowStart() == null) {
            baseline.setWindowStart(recordedAt);
        }

        int n = baseline.getEventCount() + 1;

        if (event.getAltitudeFt() != null) {
            int k = baseline.getAltitudeCount() + 1;
            double delta = event.getAltitudeFt() - orZero(baseline.getAvgAltitudeFt());
            double mean = orZero(baseline.getAvgAltitudeFt()) + delta / k;
            baseline.setM2AltitudeFt(baseline.getM2AltitudeFt() + delta * (event.getAltitudeFt() - mean));
            baseline.setAvgAltitudeFt(mean);
            baseline.setAltitudeCount(k);
        }
        if (event.getSpeedKts() != null) {
            int k = baseline.getSpeedCount() + 1;
            double delta = event.getSpeedKts() - orZero(baseline.getAvgSpeedKts());
            double mean = orZero(baseline.getAvgSpeedKts()) + delta / k;
            baseline.setM2SpeedKts(baseline.getM2SpeedKts() + delta * (event.getSpeedKts() - mean));
            baseline.setAvgSpeedKts(mean);
            baseline.setSpeedCount(k);
        }
        if (event.getHeadingDeg() != null) {
            double rad = Math.toRadians(event.getHeadingDeg());
            baseline.setSumSinHeading(baseline.getSumSinHeading() + Math.sin(rad));
            baseline.setSumCosHeading(baseline.getSumCosHeading() + Math.cos(rad));
            baseline.setHeadingCount(baseline.getHeadingCount() + 1);
            baseline.setAvgHeadingDeg(normalizeDeg(Math.toDegrees(
                    Math.atan2(baseline.getSumSinHeading(), baseline.getSumCosHeading()))));
        }
        if (event.getLat() != null && event.getLon() != null) {
            int k = baseline.getPositionCount() + 1;
            baseline.setAvgLat(orZero(baseline.getAvgLat()) + (event.getLat() - orZero(baseline.getAvgLat())) / k);
            baseline.setAvgLon(orZero(baseline.getAvgLon()) + (event.getLon() - orZero(baseline.getAvgLon())) / k);
            baseline.setPositionCount(k);
        }

        baseline.setEventCount(n);
        baseline.setCalculatedAt(Instant.now());
        baselineRepository.save(baseline);
        return n >= minEventsForBaseline;
    }

    private boolean windowExpired(Baseline baseline, Instant recordedAt) {
        return baseline.getWindowStart() != null
                && recordedAt.isAfter(baseline.getWindowStart().plus(windowLength));
    }

    /**
     * Admin action: wipe the baseline for one aircraft and start a fresh
     * window now. The next events rebuild the statistics from scratch.
     * Useful when a track was misbehaving and the old baseline is poisoned.
     */
    @Transactional
    public void resetBaseline(Long aircraftId) {
        Baseline baseline = getOrCreate(aircraftId);
        reset(baseline, Instant.now());
        baselineRepository.save(baseline);
        log.info("Baseline reset for aircraft {}", aircraftId);
    }

    private void reset(Baseline baseline, Instant recordedAt) {
        baseline.setAvgAltitudeFt(null);
        baseline.setM2AltitudeFt(0);
        baseline.setAltitudeCount(0);
        baseline.setAvgSpeedKts(null);
        baseline.setM2SpeedKts(0);
        baseline.setSpeedCount(0);
        baseline.setAvgHeadingDeg(null);
        baseline.setSumSinHeading(0);
        baseline.setSumCosHeading(0);
        baseline.setHeadingCount(0);
        baseline.setAvgLat(null);
        baseline.setAvgLon(null);
        baseline.setPositionCount(0);
        baseline.setEventCount(0);
        baseline.setWindowStart(recordedAt);
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    /** Normalize to [0, 360). */
    static double normalizeDeg(double deg) {
        double r = deg % 360.0;
        return r < 0 ? r + 360.0 : r;
    }
}
