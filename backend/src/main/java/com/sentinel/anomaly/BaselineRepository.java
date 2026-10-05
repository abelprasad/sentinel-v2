package com.sentinel.anomaly;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Baseline lookups. One row per aircraft at most; the engine resolves
 * the whole poll batch with {@link #findByAircraftIdIn} to avoid N+1.
 */
public interface BaselineRepository extends JpaRepository<Baseline, Long> {

    Optional<Baseline> findByAircraftId(Long aircraftId);

    java.util.List<Baseline> findByAircraftIdIn(java.util.Collection<Long> aircraftIds);

    /** Admin aircraft delete: remove the baseline for one aircraft. */
    void deleteByAircraftId(Long aircraftId);
}
