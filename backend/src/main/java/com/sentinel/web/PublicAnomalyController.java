package com.sentinel.web;

import com.sentinel.anomaly.Anomaly;
import com.sentinel.anomaly.AnomalyRepository;
import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.web.dto.AnomalyDto;
import com.sentinel.web.dto.DtoMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public anomaly feed. No auth — the demo map renders flags without
 * credentials. Newest first, paginated, optionally filtered to one
 * aircraft by ICAO hex.
 */
@RestController
@RequestMapping("/api/public/anomalies")
public class PublicAnomalyController {

    private final AnomalyRepository anomalyRepository;
    private final AircraftRepository aircraftRepository;
    private final DtoMapper mapper;

    public PublicAnomalyController(
            AnomalyRepository anomalyRepository,
            AircraftRepository aircraftRepository,
            DtoMapper mapper) {
        this.anomalyRepository = anomalyRepository;
        this.aircraftRepository = aircraftRepository;
        this.mapper = mapper;
    }

    @GetMapping
    public PagedResponse<AnomalyDto> anomalies(
            @RequestParam(required = false) String icaoHex,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Anomaly> page;
        if (icaoHex != null && !icaoHex.isBlank()) {
            Aircraft aircraft = aircraftRepository
                    .findByIcaoHex(icaoHex.toLowerCase())
                    .orElseThrow(() -> new ResourceNotFoundException("track", icaoHex));
            page = anomalyRepository.findByAircraftIdOrderByFlaggedAtDesc(aircraft.getId(), pageable);
        } else {
            page = anomalyRepository.findByOrderByFlaggedAtDesc(pageable);
        }

        // One batch lookup for aircraft labels — no N+1 per anomaly.
        List<Long> ids = page.getContent().stream().map(Anomaly::getAircraftId).distinct().toList();
        Map<Long, Aircraft> byId = aircraftRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Aircraft::getId, Function.identity()));

        Page<AnomalyDto> dtoPage = page.map(a -> {
            Aircraft ac = byId.get(a.getAircraftId());
            return mapper.toAnomalyDto(
                    a,
                    ac != null ? ac.getIcaoHex() : "unknown",
                    ac != null ? ac.getCallsign() : null);
        });
        return PagedResponse.from(dtoPage);
    }
}
