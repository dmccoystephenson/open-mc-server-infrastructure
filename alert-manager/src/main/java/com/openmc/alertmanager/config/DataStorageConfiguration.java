package com.openmc.alertmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataStorageConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "data.storage")
    public DataStorageConfig dataStorageConfig() {
        return new DataStorageConfig();
    }
}
