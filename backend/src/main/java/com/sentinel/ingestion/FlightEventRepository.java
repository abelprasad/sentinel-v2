package com.sentinel.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Flight event queries. The ingestion dedup check needs only the
 * latest event per aircraft — a single indexed lookup.
 */
public interface FlightEventRepository extends JpaRepository<FlightEvent, Long> {

    Optional<FlightEvent> findFirstByAircraftIdOrderByRecordedAtDesc(Long aircraftId);

    List<FlightEvent> findByAircraftIdAndRecordedAtAfterOrderByRecordedAtAsc(Long aircraftId, Instant after);

    long countByRecordedAtAfter(Instant after);
}
