package com.sentinel.anomaly;

import com.sentinel.ingestion.FlightEvent;
import org.springframework.stereotype.Component;

/**
 * Per-dimension z-score calculator.
 *
 * <p>Each telemetry dimension is scored independently against the rolling
 * baseline, then the composite score is the max. Dimensions with missing
 * data (null event field or insufficient baseline samples) are skipped —
 * a missing altitude reading must not veto a genuine heading anomaly.
 *
 * <p>Two mechanisms keep the false-positive rate down (v1 flagged
 * ~83/track/day; the target is under 5):
 *
 * <ul>
 *   <li><b>Absolute floors.</b> A z-score is only meaningful when the raw
 *       deviation is operationally significant. A 3-sigma altitude wobble
 *       of 40ft is sensor noise, not an anomaly — the floor suppresses it.</li>
 *   <li><b>Proper circular math for heading.</b> v1 averaged headings
 *       arithmetically, so a track oscillating between 359&deg; and
 *       1&deg; looked maximally anomalous. The circular distance and
 *       circular standard deviation handle the wraparound correctly.</li>
 * </ul>
 *
 * <p>Pure logic, no I/O — trivially unit-testable.
 */
@Component
public class ZScoreCalculator {

    /** Below this raw deviation, the dimension cannot trigger regardless of z-score. */
    static final double ALTITUDE_FLOOR_FT = 500.0;
    static final double SPEED_FLOOR_KTS = 25.0;
    static final double HEADING_FLOOR_DEG = 15.0;
    static final double POSITION_FLOOR_NM = 10.0;

    /** Per-dimension z-scores. Null = dimension not scorable (missing data). */
    public record ZScores(Double altitude, Double speed, Double heading, Double position) {

        /** Composite score: the strongest scorable dimension. Null when nothing is scorable. */
        public Double max() {
            Double m = null;
            for (Double z : new Double[]{altitude, speed, heading, position}) {
                if (z != null && (m == null || z > m)) {
                    m = z;
                }
            }
            return m;
        }

        /** Which dimension drove the composite score. */
        public String dominantDimension() {
            Double m = max();
            if (m == null) {
                return "none";
            }
            if (m.equals(altitude)) {
                return "altitude";
            }
            if (m.equals(speed)) {
                return "speed";
            }
            if (m.equals(heading)) {
                return "heading";
            }
            return "position";
        }
    }

    public ZScores calculate(FlightEvent event, Baseline baseline) {
        return new ZScores(
                zAltitude(event, baseline),
                zSpeed(event, baseline),
                zHeading(event, baseline),
                zPosition(event, baseline));
    }

    private Double zAltitude(FlightEvent event, Baseline baseline) {
        if (event.getAltitudeFt() == null || baseline.getAvgAltitudeFt() == null) {
            return null;
        }
        double deviation = Math.abs(event.getAltitudeFt() - baseline.getAvgAltitudeFt());
        if (deviation < ALTITUDE_FLOOR_FT) {
            return null; // sensor noise, not an anomaly
        }
        Double std = baseline.getStdAltitudeFt();
        double denom = std != null && std > 0 ? std : ALTITUDE_FLOOR_FT;
        return deviation / denom;
    }

    private Double zSpeed(FlightEvent event, Baseline baseline) {
        if (event.getSpeedKts() == null || baseline.getAvgSpeedKts() == null) {
            return null;
        }
        double deviation = Math.abs(event.getSpeedKts() - baseline.getAvgSpeedKts());
        if (deviation < SPEED_FLOOR_KTS) {
            return null;
        }
        Double std = baseline.getStdSpeedKts();
        double denom = std != null && std > 0 ? std : SPEED_FLOOR_KTS;
        return deviation / denom;
    }

    private Double zHeading(FlightEvent event, Baseline baseline) {
        if (event.getHeadingDeg() == null || baseline.getAvgHeadingDeg() == null) {
            return null;
        }
        double deviation = angularDifference(event.getHeadingDeg(), baseline.getAvgHeadingDeg());
        if (deviation < HEADING_FLOOR_DEG) {
            return null;
        }
        Double std = baseline.getCircularStdHeadingDeg();
        double denom = std != null && std > 0 ? std : HEADING_FLOOR_DEG;
        return deviation / denom;
    }

    private Double zPosition(FlightEvent event, Baseline baseline) {
        if (event.getLat() == null || event.getLon() == null
                || baseline.getAvgLat() == null || baseline.getAvgLon() == null) {
            return null;
        }
        double distanceNm = haversineNm(event.getLat(), event.getLon(),
                baseline.getAvgLat(), baseline.getAvgLon());
        if (distanceNm < POSITION_FLOOR_NM) {
            return null;
        }
        // No variance tracked for position; the floor is the scale.
        return distanceNm / POSITION_FLOOR_NM;
    }

    /**
     * Minimal angular distance in degrees, in [0, 180].
     * Handles the 0/360 wraparound: 359° and 1° are 2° apart, not 358°.
     */
    static double angularDifference(double a, double b) {
        double diff = Math.abs(a - b) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    /** Great-circle distance in nautical miles. */
    static double haversineNm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 3440.065 * c; // Earth radius in nautical miles
    }
}
