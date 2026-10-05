package com.sentinel.web;

import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.web.dto.AircraftDto;
import com.sentinel.web.dto.DtoMapper;
import com.sentinel.web.dto.UpdateAircraftRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin aircraft management. Authenticated — see {@code SecurityConfig}.
 * ICAO hex is the immutable identity; callsign and category are editable.
 */
@RestController
@RequestMapping("/api/admin/aircraft")
public class AdminAircraftController {

    private final AircraftRepository aircraftRepository;
    private final AircraftAdminService adminService;
    private final DtoMapper mapper;

    public AdminAircraftController(
            AircraftRepository aircraftRepository,
            AircraftAdminService adminService,
            DtoMapper mapper) {
        this.aircraftRepository = aircraftRepository;
        this.adminService = adminService;
        this.mapper = mapper;
    }

    @GetMapping
    public PagedResponse<AircraftDto> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<AircraftDto> page = aircraftRepository.findAll(pageable).map(mapper::toAircraftDto);
        return PagedResponse.from(page);
    }

    @GetMapping("/{id}")
    public AircraftDto get(@PathVariable Long id) {
        return aircraftRepository.findById(id)
                .map(mapper::toAircraftDto)
                .orElseThrow(() -> new ResourceNotFoundException("aircraft", String.valueOf(id)));
    }

    @PatchMapping("/{id}")
    public AircraftDto update(@PathVariable Long id, @Valid @RequestBody UpdateAircraftRequest req) {
        Aircraft aircraft = aircraftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("aircraft", String.valueOf(id)));
        if (req.callsign() != null) {
            aircraft.setCallsign(req.callsign().trim().isEmpty() ? null : req.callsign().trim());
        }
        if (req.category() != null) {
            aircraft.setCategory(req.category().trim().isEmpty() ? null : req.category().trim());
        }
        return mapper.toAircraftDto(aircraftRepository.save(aircraft));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adminService.deleteAircraft(id);
    }
}
