package com.sentinel.anomaly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;

/**
 * Rolling behavioral baseline for one aircraft.
 *
 * <p>v1 stored all-time arithmetic means, so a single early outlier
 * permanently skewed the baseline and the scorer cried wolf
 * (~142 flags/hr). v2 keeps <em>incremental</em> statistics over a
 * sliding time window:
 *
 * <ul>
 *   <li>Linear dimensions (altitude, speed, position) use Welford's
 *       online algorithm: each event updates mean and M2 (sum of
 *       squared deviations) in O(1), no history rescan.</li>
 *   <li>Heading is circular, so we keep the sum of sin/cos instead.
 *       The circular mean is atan2(sumSin, sumCos); the circular
 *       variance is 1 - R where R is the mean resultant length.
 *       Naive averaging breaks at the 0&deg;/360&deg; wraparound.</li>
 * </ul>
 *
 * <p>When an event arrives outside the current window, the statistics
 * reset and a new window starts. The window length is
 * {@code sentinel.anomaly.baseline-window-hours}.
 */
@Entity
@Table(name = "baseline")
public class Baseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aircraft_id", nullable = false, unique = true)
    private Long aircraftId;

    // --- Linear dimensions: Welford state ---

    @Column(name = "avg_altitude_ft")
    private Double avgAltitudeFt;

    @Column(name = "m2_altitude_ft", nullable = false)
    private double m2AltitudeFt;

    @Column(name = "altitude_count", nullable = false)
    private int altitudeCount;

    @Column(name = "avg_speed_kts")
    private Double avgSpeedKts;

    @Column(name = "m2_speed_kts", nullable = false)
    private double m2SpeedKts;

    @Column(name = "speed_count", nullable = false)
    private int speedCount;

    // --- Circular dimension: heading ---

    /** Cached circular mean, recomputed on every update. Null until first heading observed. */
    @Column(name = "avg_heading_deg")
    private Double avgHeadingDeg;

    @Column(name = "sum_sin_heading", nullable = false)
    private double sumSinHeading;

    @Column(name = "sum_cos_heading", nullable = false)
    private double sumCosHeading;

    @Column(name = "heading_count", nullable = false)
    private int headingCount;

    // --- Position: simple incremental mean (scored against an absolute floor) ---

    @Column(name = "avg_lat")
    private Double avgLat;

    @Column(name = "avg_lon")
    private Double avgLon;

    @Column(name = "position_count", nullable = false)
    private int positionCount;

    // --- Window bookkeeping ---

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected Baseline() {
    }

    public Baseline(Long aircraftId) {
        this.aircraftId = aircraftId;
        this.eventCount = 0;
        this.calculatedAt = Instant.now();
    }

    // --- Derived statistics (not persisted) ---

    /** Sample standard deviation of altitude, or null when fewer than 2 samples. */
    @Transient
    public Double getStdAltitudeFt() {
        return stddev(m2AltitudeFt, altitudeCount);
    }

    /** Sample standard deviation of speed, or null when fewer than 2 samples. */
    @Transient
    public Double getStdSpeedKts() {
        return stddev(m2SpeedKts, speedCount);
    }

    /**
     * Circular standard deviation of heading in degrees, or null when
     * no headings observed. Uses the wrapped-normal approximation
     * sigma = sqrt(-2 ln R), which is accurate for concentrated data
     * (the only case where a heading baseline is meaningful).
     */
    @Transient
    public Double getCircularStdHeadingDeg() {
        if (headingCount == 0) {
            return null;
        }
        double r = Math.hypot(sumSinHeading, sumCosHeading) / headingCount;
        if (r >= 1.0) {
            return 0.0; // all headings identical
        }
        if (r <= 0.0) {
            return null; // uniform spread — no meaningful baseline
        }
        return Math.toDegrees(Math.sqrt(-2.0 * Math.log(r)));
    }

    private static Double stddev(double m2, int count) {
        if (count < 2) {
            return null;
        }
        return Math.sqrt(m2 / (count - 1));
    }

    // --- Getters / setters ---

    public Long getId() { return id; }
    public Long getAircraftId() { return aircraftId; }

    public Double getAvgAltitudeFt() { return avgAltitudeFt; }
    public void setAvgAltitudeFt(Double avgAltitudeFt) { this.avgAltitudeFt = avgAltitudeFt; }
    public double getM2AltitudeFt() { return m2AltitudeFt; }
    public void setM2AltitudeFt(double m2AltitudeFt) { this.m2AltitudeFt = m2AltitudeFt; }
    public int getAltitudeCount() { return altitudeCount; }
    public void setAltitudeCount(int altitudeCount) { this.altitudeCount = altitudeCount; }

    public Double getAvgSpeedKts() { return avgSpeedKts; }
    public void setAvgSpeedKts(Double avgSpeedKts) { this.avgSpeedKts = avgSpeedKts; }
    public double getM2SpeedKts() { return m2SpeedKts; }
    public void setM2SpeedKts(double m2SpeedKts) { this.m2SpeedKts = m2SpeedKts; }
    public int getSpeedCount() { return speedCount; }
    public void setSpeedCount(int speedCount) { this.speedCount = speedCount; }

    public Double getAvgHeadingDeg() { return avgHeadingDeg; }
    public void setAvgHeadingDeg(Double avgHeadingDeg) { this.avgHeadingDeg = avgHeadingDeg; }
    public double getSumSinHeading() { return sumSinHeading; }
    public void setSumSinHeading(double sumSinHeading) { this.sumSinHeading = sumSinHeading; }
    public double getSumCosHeading() { return sumCosHeading; }
    public void setSumCosHeading(double sumCosHeading) { this.sumCosHeading = sumCosHeading; }
    public int getHeadingCount() { return headingCount; }
    public void setHeadingCount(int headingCount) { this.headingCount = headingCount; }

    public Double getAvgLat() { return avgLat; }
    public void setAvgLat(Double avgLat) { this.avgLat = avgLat; }
    public Double getAvgLon() { return avgLon; }
    public void setAvgLon(Double avgLon) { this.avgLon = avgLon; }
    public int getPositionCount() { return positionCount; }
    public void setPositionCount(int positionCount) { this.positionCount = positionCount; }

    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }
}
