-- SENTINEL v2 — anomaly engine state
--
-- v1 kept all-time means and naive heading averages in `baseline`, which
-- made the scorer flag ~142 anomalies/hour on 41 tracks. v2 stores the
-- incremental state needed for rolling-window statistics:
--
--   * Welford M2 (sum of squared deviations) per linear dimension, so
--     mean/variance update in O(1) per event without rescanning history.
--   * Sum of sin/cos of heading, for proper circular mean and variance
--     (naive arithmetic averaging breaks at the 0/360 wraparound).

-- Welford state for linear dimensions
ALTER TABLE baseline ADD COLUMN m2_altitude_ft DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE baseline ADD COLUMN m2_speed_kts DOUBLE PRECISION NOT NULL DEFAULT 0;

-- Per-dimension sample counts. Telemetry fields are intermittently null
-- (an aircraft may report position without altitude), and Welford's update
-- divides by the per-dimension n — using the global event count would
-- silently corrupt the mean and variance.
ALTER TABLE baseline ADD COLUMN altitude_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE baseline ADD COLUMN speed_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE baseline ADD COLUMN heading_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE baseline ADD COLUMN position_count INTEGER NOT NULL DEFAULT 0;

-- Circular heading state
ALTER TABLE baseline ADD COLUMN sum_sin_heading DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE baseline ADD COLUMN sum_cos_heading DOUBLE PRECISION NOT NULL DEFAULT 0;

-- Escalation: a follow-up anomaly can point at the anomaly it escalates,
-- so the UI can render "this got worse" threads instead of flat alert spam.
ALTER TABLE anomaly ADD COLUMN parent_anomaly_id BIGINT REFERENCES anomaly (id) ON DELETE SET NULL;
CREATE INDEX idx_anomaly_parent ON anomaly (parent_anomaly_id);

-- Track lifecycle: NEW (no baseline yet) -> ACTIVE -> STALE -> LOST.
-- Maintained by the anomaly engine from last_seen timestamps.
ALTER TABLE aircraft ADD COLUMN track_state VARCHAR(16) NOT NULL DEFAULT 'NEW'
    CONSTRAINT chk_aircraft_track_state CHECK (track_state IN ('NEW', 'ACTIVE', 'STALE', 'LOST'));
CREATE INDEX idx_aircraft_track_state ON aircraft (track_state);
