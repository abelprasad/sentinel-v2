package com.sentinel.anomaly;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Anomaly queries. The dashboard lists unacknowledged flags newest-first;
 * the engine's cooldown check needs only the latest flag per aircraft.
 */
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    /** Newest-first history for one aircraft. */
    List<Anomaly> findByAircraftIdOrderByFlaggedAtDesc(Long aircraftId);

    /** Dashboard queue: everything awaiting analyst review. */
    List<Anomaly> findByAcknowledgedFalseOrderByFlaggedAtDesc();

    /** Cooldown check: the most recent flag for this aircraft, if any. */
    Optional<Anomaly> findFirstByAircraftIdOrderByFlaggedAtDesc(Long aircraftId);

    /** Escalation threads: follow-ups linked to a parent anomaly. */
    List<Anomaly> findByParentAnomalyIdOrderByFlaggedAtAsc(Long parentAnomalyId);

    long countByFlaggedAtAfter(Instant after);
}
