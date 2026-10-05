package com.sentinel.anomaly;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Paginated variant for the public anomalies endpoint. */
    Page<Anomaly> findByOrderByFlaggedAtDesc(Pageable pageable);

    /** Paginated per-aircraft history. */
    Page<Anomaly> findByAircraftIdOrderByFlaggedAtDesc(Long aircraftId, Pageable pageable);

    /** Admin queue filter: acknowledged or not, newest first. */
    Page<Anomaly> findByAcknowledgedOrderByFlaggedAtDesc(boolean acknowledged, Pageable pageable);

    /** Cooldown check: the most recent flag for this aircraft, if any. */
    Optional<Anomaly> findFirstByAircraftIdOrderByFlaggedAtDesc(Long aircraftId);

    /** Escalation threads: follow-ups linked to a parent anomaly. */
    List<Anomaly> findByParentAnomalyIdOrderByFlaggedAtAsc(Long parentAnomalyId);

    long countByFlaggedAtAfter(Instant after);

    /** Admin aircraft delete: remove all flags for one aircraft. */
    void deleteByAircraftId(Long aircraftId);

    /** Status endpoint: how many flags are awaiting analyst review. */
    long countByAcknowledgedFalse();

    /** Status endpoint: how many flags have been escalated. */
    long countByEscalatedTrue();
}
