package com.sentinel.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Shared HTTP clients with explicit timeouts.
 *
 * The v1 backend constructed a {@code new RestTemplate()} per ADS-B poll
 * with no timeouts — a hung upstream would stall the scheduler thread
 * indefinitely. These beans are singletons with connect/read timeouts
 * wired from {@link SentinelProperties}.
 */
@Configuration
public class HttpClientConfig {

    /**
     * Client for the ADS-B telemetry feed.
     */
    @Bean("adsbRestClient")
    public RestClient adsbRestClient(SentinelProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.adsb().connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.adsb().readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Client for the LLM explanation provider.
     */
    @Bean("llmRestClient")
    public RestClient llmRestClient(SentinelProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.llm().connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.llm().readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
