package com.sentinel.anomaly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A flagged behavioral anomaly for an aircraft.
 *
 * <p>Each row captures the per-dimension z-scores that triggered it, so
 * the UI can explain <em>why</em> without recomputing statistics. The
 * {@code explanation} is a rule-based template by default
 * ({@code explanationSrc='rule'}); the LLM phase upgrades it to
 * natural language ({@code explanationSrc='llm'}).
 *
 * <p>Related anomalies form escalation threads via {@code parentAnomalyId}:
 * when a track keeps misbehaving inside the cooldown window, the new
 * flag links to its parent instead of spamming a standalone alert.
 */
@Entity
@Table(name = "anomaly")
public class Anomaly {

    /** Rule-based template explanation. */
    public static final String SRC_RULE = "rule";
    /** LLM-generated explanation. */
    public static final String SRC_LLM = "llm";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aircraft_id", nullable = false)
    private Long aircraftId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** Composite score: the max per-dimension z-score. */
    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "z_altitude")
    private Double zAltitude;

    @Column(name = "z_speed")
    private Double zSpeed;

    @Column(name = "z_heading")
    private Double zHeading;

    @Column(name = "z_position")
    private Double zPosition;

    @Column(name = "explanation")
    private String explanation;

    @Column(name = "explanation_src", nullable = false, length = 16)
    private String explanationSrc = SRC_RULE;

    /** Null for standalone flags; set when this escalates a recent anomaly. */
    @Column(name = "parent_anomaly_id")
    private Long parentAnomalyId;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "escalated", nullable = false)
    private boolean escalated;

    protected Anomaly() {
    }

    public Anomaly(Long aircraftId, Long eventId, double score) {
        this.aircraftId = aircraftId;
        this.eventId = eventId;
        this.score = score;
        this.flaggedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAircraftId() { return aircraftId; }
    public Long getEventId() { return eventId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public Double getZAltitude() { return zAltitude; }
    public void setZAltitude(Double zAltitude) { this.zAltitude = zAltitude; }
    public Double getZSpeed() { return zSpeed; }
    public void setZSpeed(Double zSpeed) { this.zSpeed = zSpeed; }
    public Double getZHeading() { return zHeading; }
    public void setZHeading(Double zHeading) { this.zHeading = zHeading; }
    public Double getZPosition() { return zPosition; }
    public void setZPosition(Double zPosition) { this.zPosition = zPosition; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getExplanationSrc() { return explanationSrc; }
    public void setExplanationSrc(String explanationSrc) { this.explanationSrc = explanationSrc; }

    public Long getParentAnomalyId() { return parentAnomalyId; }
    public void setParentAnomalyId(Long parentAnomalyId) { this.parentAnomalyId = parentAnomalyId; }

    public Instant getFlaggedAt() { return flaggedAt; }
    public void setFlaggedAt(Instant flaggedAt) { this.flaggedAt = flaggedAt; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public boolean isEscalated() { return escalated; }
    public void setEscalated(boolean escalated) { this.escalated = escalated; }
}
