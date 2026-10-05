package com.sentinel.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.config.SentinelProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * AdsbClient parsing tests — no HTTP involved, we drive
 * {@link AdsbClient#parse(String)} directly with canned payloads.
 */
class AdsbClientTest {

    private AdsbClient client;

    @BeforeEach
    void setUp() {
        // RestClient and props are unused by parse(); nulls are fine here.
        client = new AdsbClient(RestClient.create(), null, new ObjectMapper());
    }

    @Test
    void parsesAircraftArray() {
        String json = """
                {
                  "ac": [
                    {"hex": "a1b2c3", "flight": "UAL123 ", "lat": 40.1, "lon": -75.1,
                     "alt_baro": 35000, "gs": 450.0, "track": 90.0, "t": "B738", "category": "A3"},
                    {"hex": "d4e5f6", "lat": 40.2, "lon": -75.2, "alt_baro": "ground", "gs": 0.0}
                  ]
                }""";

        List<AdsbAircraft> result = client.parse(json);

        assertThat(result).hasSize(2);
        AdsbAircraft first = result.get(0);
        assertThat(first.hex()).isEqualTo("a1b2c3");
        assertThat(first.callsign()).isEqualTo("UAL123");
        assertThat(first.altitudeFt()).isEqualTo(35000.0);
        assertThat(first.type()).isEqualTo("B738");
        // "ground" -> null altitude, not a crash
        assertThat(result.get(1).altitudeFt()).isNull();
    }

    @Test
    void dropsRecordsWithoutPosition() {
        String json = """
                {"ac": [
                  {"hex": "nopos", "flight": "NOPOS"},
                  {"hex": "ok1", "lat": 40.0, "lon": -75.0}
                ]}""";

        List<AdsbAircraft> result = client.parse(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hex()).isEqualTo("ok1");
    }

    @Test
    void emptyOnBlankOrGarbage() {
        assertThat(client.parse(null)).isEmpty();
        assertThat(client.parse("")).isEmpty();
        assertThat(client.parse("not json")).isEmpty();
        assertThat(client.parse("{\"msg\": \"no aircraft here\"}")).isEmpty();
    }

    @Test
    void supportsAircraftArrayName() {
        String json = """
                {"aircraft": [{"hex": "abc123", "lat": 41.0, "lon": -76.0}]}""";

        assertThat(client.parse(json)).hasSize(1);
    }
}
