package com.sentinel.web.dto;

import com.sentinel.anomaly.Anomaly;
import com.sentinel.anomaly.Baseline;
import com.sentinel.ingestion.Aircraft;
import com.sentinel.ingestion.FlightEvent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Entity-to-DTO conversions. Kept in one place so controllers stay thin
 * and the wire format has a single owner.
 */
@Component
public class DtoMapper {

    public AircraftDto toAircraftDto(Aircraft a) {
        return new AircraftDto(
                a.getIcaoHex(), a.getCallsign(), a.getCategory(),
                a.getTrackState(), a.getFirstSeen(), a.getLastSeen());
    }

    public TrackPointDto toTrackPointDto(FlightEvent e) {
        return new TrackPointDto(
                e.getLat(), e.getLon(), e.getAltitudeFt(),
                e.getSpeedKts(), e.getHeadingDeg(), e.getRecordedAt());
    }

    public TrackDto toTrackDto(Aircraft aircraft, List<FlightEvent> events) {
        return new TrackDto(
                toAircraftDto(aircraft),
                events.stream().map(this::toTrackPointDto).toList());
    }

    public AnomalyDto toAnomalyDto(Anomaly a, String icaoHex, String callsign) {
        return new AnomalyDto(
                a.getId(), icaoHex, callsign, a.getScore(),
                a.getZAltitude(), a.getZSpeed(), a.getZHeading(), a.getZPosition(),
                a.getExplanation(), a.getExplanationSrc(), a.getParentAnomalyId(),
                a.isAcknowledged(), a.isEscalated(), a.getFlaggedAt());
    }

    public BaselineDto toBaselineDto(Baseline b, String icaoHex, int minEventsForBaseline) {
        int total = b.getAltitudeCount() + b.getSpeedCount() + b.getHeadingCount();
        return new BaselineDto(
                icaoHex,
                b.getAvgAltitudeFt(), b.getAltitudeCount(),
                b.getAvgSpeedKts(), b.getSpeedCount(),
                b.getAvgHeadingDeg(), b.getHeadingCount(),
                b.getWindowStart(),
                total >= minEventsForBaseline);
    }
}
