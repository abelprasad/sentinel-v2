-- Analyst workflow: anomalies can be escalated for deeper review,
-- independently of acknowledgement.
ALTER TABLE anomaly ADD COLUMN escalated BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_anomaly_escalated ON anomaly (escalated) WHERE escalated;
