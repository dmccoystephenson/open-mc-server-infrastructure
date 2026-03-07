package com.openmc.agentmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    @Value("${backup.manager.read-timeout-seconds:600}")
    private int backupReadTimeoutSeconds;

    /**
     * Create a configured RestTemplate bean
     */
    @Primary
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return new RestTemplate(factory);
    }

    /**
     * Create a RestTemplate with a longer read timeout for backup operations,
     * which run synchronously and can take several minutes.
     */
    @Bean
    public RestTemplate backupRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(backupReadTimeoutSeconds));
        return new RestTemplate(factory);
    }
}
