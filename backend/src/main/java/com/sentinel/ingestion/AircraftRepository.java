package com.sentinel.ingestion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aircraft lookups. {@link #findByIcaoHexIn} exists so the ingestion
 * service can resolve a whole poll batch in one query instead of
 * N+1 single lookups (v1 did one query per aircraft per poll).
 */
public interface AircraftRepository extends JpaRepository<Aircraft, Long> {

    Optional<Aircraft> findByIcaoHex(String icaoHex);

    List<Aircraft> findByIcaoHexIn(Collection<String> icaoHexes);

    List<Aircraft> findByTrackState(String trackState);
}
