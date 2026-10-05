package com.sentinel.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A tracked aircraft, keyed by ICAO 24-bit hex address.
 *
 * <p>Rows are created on first sighting by the ingestion service and
 * never deleted by it — pruning is a separate scheduled concern.
 */
@Entity
@Table(name = "aircraft")
public class Aircraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "icao_hex", nullable = false, unique = true, length = 16)
    private String icaoHex;

    @Column(name = "callsign", length = 16)
    private String callsign;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected Aircraft() {
    }

    public Aircraft(String icaoHex) {
        this.icaoHex = icaoHex;
        this.firstSeen = Instant.now();
        this.lastSeen = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIcaoHex() {
        return icaoHex;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void touch() {
        this.lastSeen = Instant.now();
    }
}
