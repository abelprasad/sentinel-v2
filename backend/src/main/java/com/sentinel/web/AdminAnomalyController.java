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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin anomaly queue. Authenticated — see {@code SecurityConfig}.
 * Acknowledge is one-way (a reviewed flag stays reviewed); escalate
 * can be set and cleared as the investigation evolves.
 */
@RestController
@RequestMapping("/api/admin/anomalies")
public class AdminAnomalyController {

    private final AnomalyRepository anomalyRepository;
    private final AircraftRepository aircraftRepository;
    private final DtoMapper mapper;

    public AdminAnomalyController(
            AnomalyRepository anomalyRepository,
            AircraftRepository aircraftRepository,
            DtoMapper mapper) {
        this.anomalyRepository = anomalyRepository;
        this.aircraftRepository = aircraftRepository;
        this.mapper = mapper;
    }

    @GetMapping
    public PagedResponse<AnomalyDto> list(
            @RequestParam(required = false) Boolean acknowledged,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Anomaly> page = acknowledged == null
                ? anomalyRepository.findByOrderByFlaggedAtDesc(pageable)
                : anomalyRepository.findByAcknowledgedOrderByFlaggedAtDesc(acknowledged, pageable);

        // One batch lookup for aircraft labels — no N+1 per anomaly.
        List<Long> ids = page.getContent().stream().map(Anomaly::getAircraftId).distinct().toList();
        Map<Long, Aircraft> byId = aircraftRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Aircraft::getId, Function.identity()));

        return PagedResponse.from(page.map(a -> toDto(a, byId.get(a.getAircraftId()))));
    }

    @GetMapping("/{id}")
    public AnomalyDto get(@PathVariable Long id) {
        return toDto(findOr404(id), null);
    }

    /** Mark a flag as reviewed. Idempotent. */
    @PostMapping("/{id}/acknowledge")
    @Transactional
    public AnomalyDto acknowledge(@PathVariable Long id) {
        Anomaly anomaly = findOr404(id);
        anomaly.setAcknowledged(true);
        return toDto(anomalyRepository.save(anomaly), null);
    }

    /** Escalate a flag for deeper review. Idempotent. */
    @PostMapping("/{id}/escalate")
    @Transactional
    public AnomalyDto escalate(@PathVariable Long id) {
        Anomaly anomaly = findOr404(id);
        anomaly.setEscalated(true);
        return toDto(anomalyRepository.save(anomaly), null);
    }

    /** Clear an escalation. */
    @DeleteMapping("/{id}/escalate")
    @Transactional
    public AnomalyDto deescalate(@PathVariable Long id) {
        Anomaly anomaly = findOr404(id);
        anomaly.setEscalated(false);
        return toDto(anomalyRepository.save(anomaly), null);
    }

    private Anomaly findOr404(Long id) {
        return anomalyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("anomaly", String.valueOf(id)));
    }

    private AnomalyDto toDto(Anomaly anomaly, Aircraft aircraft) {
        Aircraft ac = aircraft != null
                ? aircraft
                : aircraftRepository.findById(anomaly.getAircraftId()).orElse(null);
        return mapper.toAnomalyDto(
                anomaly,
                ac != null ? ac.getIcaoHex() : "unknown",
                ac != null ? ac.getCallsign() : null);
    }
}
