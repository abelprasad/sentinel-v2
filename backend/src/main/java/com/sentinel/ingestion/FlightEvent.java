package com.sentinel.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One telemetry snapshot for an aircraft.
 *
 * <p>Uses a raw {@code aircraftId} FK instead of a {@code @ManyToOne}
 * association — the ingestion service writes events in bulk and never
 * needs the parent entity hydrated.
 */
@Entity
@Table(name = "flight_event")
public class FlightEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aircraft_id", nullable = false)
    private Long aircraftId;

    @Column(name = "altitude_ft")
    private Double altitudeFt;

    @Column(name = "speed_kts")
    private Double speedKts;

    @Column(name = "heading_deg")
    private Double headingDeg;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected FlightEvent() {
    }

    public FlightEvent(Long aircraftId, Instant recordedAt) {
        this.aircraftId = aircraftId;
        this.recordedAt = recordedAt;
        this.ingestedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAircraftId() { return aircraftId; }
    public Double getAltitudeFt() { return altitudeFt; }
    public void setAltitudeFt(Double altitudeFt) { this.altitudeFt = altitudeFt; }
    public Double getSpeedKts() { return speedKts; }
    public void setSpeedKts(Double speedKts) { this.speedKts = speedKts; }
    public Double getHeadingDeg() { return headingDeg; }
    public void setHeadingDeg(Double headingDeg) { this.headingDeg = headingDeg; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
}
