package com.sentinel.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.sentinel.ingestion.FlightEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for z-score math — no Spring, no DB.
 */
class ZScoreCalculatorTest {

    private ZScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ZScoreCalculator();
    }

    private FlightEvent event(Double alt, Double speed, Double heading, Double lat, Double lon) {
        FlightEvent e = new FlightEvent(1L, Instant.now());
        e.setAltitudeFt(alt);
        e.setSpeedKts(speed);
        e.setHeadingDeg(heading);
        e.setLat(lat);
        e.setLon(lon);
        return e;
    }

    private Baseline baseline(double altMean, double altStd, double speedMean, double speedStd,
            double headingMean, double latMean, double lonMean) {
        Baseline b = new Baseline(1L);
        // Reverse-engineer Welford state: M2 = std^2 * (n-1) with n=100
        int n = 100;
        b.setAvgAltitudeFt(altMean);
        b.setM2AltitudeFt(altStd * altStd * (n - 1));
        b.setAltitudeCount(n);
        b.setAvgSpeedKts(speedMean);
        b.setM2SpeedKts(speedStd * speedStd * (n - 1));
        b.setSpeedCount(n);
        double rad = Math.toRadians(headingMean);
        // Concentrated headings: R close to 1
        b.setSumSinHeading(Math.sin(rad) * n);
        b.setSumCosHeading(Math.cos(rad) * n);
        b.setHeadingCount(n);
        b.setAvgHeadingDeg(headingMean);
        b.setAvgLat(latMean);
        b.setAvgLon(lonMean);
        b.setPositionCount(n);
        b.setEventCount(n);
        return b;
    }

    @Test
    void altitudeZScore() {
        // 3 sigma above: (11000 - 10000) / 500 = 2... use exact numbers
        Baseline b = baseline(10000, 500, 400, 20, 90, 40.0, -75.0);
        FlightEvent e = event(11500.0, 400.0, 90.0, 40.0, -75.0);
        ZScoreCalculator.ZScores z = calculator.calculate(e, b);
        assertThat(z.altitude()).isCloseTo(3.0, within(0.01));
        assertThat(z.max()).isCloseTo(3.0, within(0.01));
        assertThat(z.dominantDimension()).isEqualTo("altitude");
    }

    @Test
    void floorSuppressesSmallDeviations() {
        Baseline b = baseline(10000, 10, 400, 5, 90, 40.0, -75.0);
        // 100ft deviation with 10ft std = z=10, but below the 500ft floor
        FlightEvent e = event(10100.0, 400.0, 90.0, 40.0, -75.0);
        ZScoreCalculator.ZScores z = calculator.calculate(e, b);
        assertThat(z.altitude()).isNull();
    }

    @Test
    void headingWraparound() {
        // Baseline at 359°, event at 1° — 2° apart, not 358°
        assertThat(ZScoreCalculator.angularDifference(359.0, 1.0)).isCloseTo(2.0, within(0.001));
        assertThat(ZScoreCalculator.angularDifference(1.0, 359.0)).isCloseTo(2.0, within(0.001));
        assertThat(ZScoreCalculator.angularDifference(90.0, 270.0)).isCloseTo(180.0, within(0.001));
        assertThat(ZScoreCalculator.angularDifference(0.0, 0.0)).isCloseTo(0.0, within(0.001));
    }

    @Test
    void headingNearWraparoundNotAnomalous() {
        Baseline b = baseline(10000, 500, 400, 20, 359, 40.0, -75.0);
        FlightEvent e = event(10000.0, 400.0, 1.0, 40.0, -75.0);
        ZScoreCalculator.ZScores z = calculator.calculate(e, b);
        // 2° deviation, below the 15° floor
        assertThat(z.heading()).isNull();
    }

    @Test
    void nullFieldsAreSkippedNotVetoed() {
        Baseline b = baseline(10000, 500, 400, 20, 90, 40.0, -75.0);
        // No altitude reported, but heading is 90° off
        FlightEvent e = event(null, 400.0, 180.0, 40.0, -75.0);
        ZScoreCalculator.ZScores z = calculator.calculate(e, b);
        assertThat(z.altitude()).isNull();
        assertThat(z.heading()).isNotNull();
        assertThat(z.max()).isEqualTo(z.heading());
    }

    @Test
    void nothingScorableGivesNullMax() {
        Baseline b = new Baseline(1L); // empty baseline
        FlightEvent e = event(null, null, null, null, null);
        ZScoreCalculator.ZScores z = calculator.calculate(e, b);
        assertThat(z.max()).isNull();
        assertThat(z.dominantDimension()).isEqualTo("none");
    }

    @Test
    void haversineDistance() {
        // ~60nm per degree of latitude
        double nm = ZScoreCalculator.haversineNm(40.0, -75.0, 41.0, -75.0);
        assertThat(nm).isCloseTo(60.0, within(1.0));
    }

    @Test
    void positionFarFromBaseline() {
        Baseline b = baseline(10000, 500, 400, 20, 90, 40.0, -75.0);
        // ~60nm away, floor is 10nm -> z ~= 6
        FlightEvent e = event(10000.0, 400.0, 90.0, 41.0, -75.0);
        ZScoreCalculator.ZScores z = calculator.calculate(e, b);
        assertThat(z.position()).isCloseTo(6.0, within(0.5));
    }
}
