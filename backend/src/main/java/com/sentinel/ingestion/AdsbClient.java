package com.sentinel.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.config.SentinelProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches live ADS-B telemetry from the configured upstream feed.
 *
 * <p>Uses the shared {@code adsbRestClient} bean (connect/read timeouts
 * wired from config). v1 constructed a {@code new RestTemplate()} per
 * poll with no timeouts — a hung upstream stalled the scheduler thread
 * indefinitely.
 *
 * <p>Failures are logged and surfaced as an empty list; the scheduler
 * keeps running and retries on the next poll.
 */
@Component
public class AdsbClient {

    private static final Logger log = LoggerFactory.getLogger(AdsbClient.class);

    private final RestClient http;
    private final SentinelProperties props;
    private final ObjectMapper mapper;

    public AdsbClient(
            @Qualifier(adsbRestClient) RestClient http,
            SentinelProperties props,
            ObjectMapper mapper) {
        this.http = http;
        this.props = props;
        this.mapper = mapper;
    }

    /** Fetch the current aircraft snapshot. Never throws — returns empty on failure. */
    public List<AdsbAircraft> fetch() {
        String body;
        try {
            body = http.get()
                    .uri(props.adsb().url())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn(ADS-B fetch failed: {}, e.getMessage());
            return List.of();
        }
        return parse(body);
    }

    /**
     * Parse a raw adsb.lol response body into aircraft records.
     * Package-private for testing without HTTP.
     */
    List<AdsbAircraft> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode array = root.path("ac");
            if (!array.isArray()) {
                array = root.path("aircraft");
            }
            if (!array.isArray()) {
                log.warn("ADS-B response has no ac/aircraft array");
                return List.of();
            }
            List<AdsbAircraft> out = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                try {
                    AdsbAircraft ac = mapper.treeToValue(node, AdsbAircraft.class);
                    if (ac.hasPosition()) {
                        out.add(ac);
                    }
                } catch (Exception e) {
                    log.debug("Skipping unparsable aircraft record: {}", e.getMessage());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("ADS-B parse failed: {}", e.getMessage());
            return List.of();
        }
    }
}
