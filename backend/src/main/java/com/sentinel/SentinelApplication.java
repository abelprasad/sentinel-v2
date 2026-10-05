package com.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SENTINEL v2 — AI airspace anomaly detection.
 *
 * Ingests live ADS-B telemetry, learns per-aircraft behavioral baselines,
 * scores deviations, and explains anomalies via LLM.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.sentinel.config")
@EnableScheduling
@EnableAsync
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
    }
}
