package com.sentinel.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Flight event queries. The ingestion dedup check needs only the
 * latest event per aircraft — a single indexed lookup.
 */
public interface FlightEventRepository extends JpaRepository<FlightEvent, Long> {

    Optional<FlightEvent> findFirstByAircraftIdOrderByRecordedAtDesc(Long aircraftId);

    List<FlightEvent> findByAircraftIdAndRecordedAtAfterOrderByRecordedAtAsc(Long aircraftId, Instant after);

    /** Track endpoint: newest N points, capped by Pageable. */
    List<FlightEvent> findByAircraftIdOrderByRecordedAtDesc(Long aircraftId, Pageable pageable);

    long countByRecordedAtAfter(Instant after);

    /** Engine watermark: every event after the last processed id, oldest first. */
    List<FlightEvent> findByIdGreaterThanOrderByIdAsc(Long id);

    /** Boot watermark: the newest event id, so restarts never rescore history. */
    Optional<FlightEvent> findFirstByOrderByIdDesc();
}
