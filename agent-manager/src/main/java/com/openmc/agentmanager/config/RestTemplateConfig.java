package com.openmc.agentmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for HTTP clients
 */
@Configuration
public class RestTemplateConfig {

    @Value("${http.client.connect-timeout-seconds:5}")
    private int connectTimeoutSeconds;

    @Value("${http.client.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    /**
     * Create a configured RestTemplate bean
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return new RestTemplate(factory);
    }
}
