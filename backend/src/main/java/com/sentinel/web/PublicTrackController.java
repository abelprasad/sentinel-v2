package com.sentinel.web;

import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.AircraftRepository;
import com.sentinel.ingestion.FlightEvent;
import com.sentinel.ingestion.FlightEventRepository;
import com.sentinel.web.dto.DtoMapper;
import com.sentinel.web.dto.TrackDto;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public track lookup by ICAO hex. No auth — the map UI needs this
 * without credentials.
 *
 * <p>v1 bug fix: a missing track returned 403 because the authorization
 * check ran before the existence check. Here existence is checked first
 * and a missing track is a 404, full stop.
 */
@RestController
@RequestMapping("/api/public/tracks")
public class PublicTrackController {

    /** Max points per track response — bounds payload size for the map. */
    private static final int MAX_POINTS = 200;

    private final AircraftRepository aircraftRepository;
    private final FlightEventRepository eventRepository;
    private final DtoMapper mapper;

    public PublicTrackController(
            AircraftRepository aircraftRepository,
            FlightEventRepository eventRepository,
            DtoMapper mapper) {
        this.aircraftRepository = aircraftRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
    }

    @GetMapping("/{icaoHex}")
    public TrackDto track(@PathVariable String icaoHex) {
        Aircraft aircraft = aircraftRepository
                .findByIcaoHex(icaoHex.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("track", icaoHex));

        List<FlightEvent> recent = eventRepository
                .findByAircraftIdOrderByRecordedAtDesc(
                        aircraft.getId(), PageRequest.of(0, MAX_POINTS));

        // Wire format is oldest-first; the query is newest-first for the limit.
        Collections.reverse(recent);
        return mapper.toTrackDto(aircraft, recent);
    }
}
