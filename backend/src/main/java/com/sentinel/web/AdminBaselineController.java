package com.sentinel.web;

import com.sentinel.anomaly.Baseline;
import com.sentinel.anomaly.BaselineRepository;
import com.sentinel.anomaly.BaselineService;
import com.sentinel.config.SentinelProperties;
import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.web.dto.BaselineDto;
import com.sentinel.web.dto.DtoMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin baseline inspection. Authenticated — see {@code SecurityConfig}.
 * Resetting is for poisoned baselines (e.g. a track that was spoofing
 * and has since gone legitimate) — the next events rebuild from scratch.
 */
@RestController
@RequestMapping("/api/admin/baselines")
public class AdminBaselineController {

    private final BaselineRepository baselineRepository;
    private final BaselineService baselineService;
    private final AircraftRepository aircraftRepository;
    private final DtoMapper mapper;
    private final int minEventsForBaseline;

    public AdminBaselineController(
            BaselineRepository baselineRepository,
            BaselineService baselineService,
            AircraftRepository aircraftRepository,
            DtoMapper mapper,
            SentinelProperties properties) {
        this.baselineRepository = baselineRepository;
        this.baselineService = baselineService;
        this.aircraftRepository = aircraftRepository;
        this.mapper = mapper;
        this.minEventsForBaseline = properties.anomaly().minEventsForBaseline();
    }

    @GetMapping
    public PagedResponse<BaselineDto> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<Baseline> page = baselineRepository.findAll(pageable);

        List<Long> ids = page.getContent().stream().map(Baseline::getAircraftId).distinct().toList();
        Map<Long, Aircraft> byId = aircraftRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Aircraft::getId, Function.identity()));

        Page<BaselineDto> dtoPage = page.map(b -> {
            Aircraft ac = byId.get(b.getAircraftId());
            return mapper.toBaselineDto(
                    b,
                    ac != null ? ac.getIcaoHex() : "unknown",
                    minEventsForBaseline);
        });
        return PagedResponse.from(dtoPage);
    }

    @GetMapping("/{aircraftId}")
    public BaselineDto get(@PathVariable Long aircraftId) {
        Aircraft aircraft = aircraftRepository.findById(aircraftId)
                .orElseThrow(() -> new ResourceNotFoundException("aircraft", String.valueOf(aircraftId)));
        Baseline baseline = baselineRepository.findByAircraftId(aircraftId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "no baseline yet for aircraft " + aircraft.getIcaoHex()));
        return mapper.toBaselineDto(baseline, aircraft.getIcaoHex(), minEventsForBaseline);
    }

    /** Wipe the baseline and start a fresh window now. Idempotent. */
    @PostMapping("/{aircraftId}/reset")
    public BaselineDto reset(@PathVariable Long aircraftId) {
        Aircraft aircraft = aircraftRepository.findById(aircraftId)
                .orElseThrow(() -> new ResourceNotFoundException("aircraft", String.valueOf(aircraftId)));
        baselineService.resetBaseline(aircraftId);
        Baseline baseline = baselineRepository.findByAircraftId(aircraftId).orElseThrow();
        return mapper.toBaselineDto(baseline, aircraft.getIcaoHex(), minEventsForBaseline);
    }
}
