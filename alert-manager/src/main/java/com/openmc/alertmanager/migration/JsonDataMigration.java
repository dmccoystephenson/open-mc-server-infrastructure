package com.openmc.alertmanager.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.alertmanager.config.DataStorageConfig;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

/**
 * One-time migration runner that reads existing JSON alert history and inserts it
 * into the PostgreSQL database. Idempotent: skips migration if the database already
 * contains alert records.
 */
@Slf4j
@Component
public class JsonDataMigration implements ApplicationRunner {

    private final DataStorageConfig dataStorageConfig;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public JsonDataMigration(DataStorageConfig dataStorageConfig,
                             AlertRepository alertRepository) {
        this.dataStorageConfig = dataStorageConfig;
        this.alertRepository = alertRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateAlertRecords();
    }

    private void migrateAlertRecords() {
        if (alertRepository.existsByIdIsNotNull()) {
            log.info("Alert records already exist in database, skipping JSON migration");
            return;
        }

        File file = new File(dataStorageConfig.getFilePath("alert-history.json"));
        if (!file.exists()) {
            log.info("No alert-history.json file found, skipping migration");
            return;
        }

        try {
            AlertRecord[] records = objectMapper.readValue(file, AlertRecord[].class);
            alertRepository.saveAll(Arrays.asList(records));
            log.info("Migrated {} alert records from JSON to database", records.length);
        } catch (Exception e) {
            log.error("Failed to migrate alert records from JSON", e);
        }
    }
}
