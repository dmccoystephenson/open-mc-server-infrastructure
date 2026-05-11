package com.openmc.alertmanager.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.alertmanager.config.DataStorageConfig;
import com.openmc.alertmanager.entity.AlertRecordEntity;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.repository.jpa.AlertRecordJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

@Slf4j
@Component
public class JsonToPostgresMigration implements ApplicationRunner {

    private static final String FILENAME = "alert-history.json";

    private final DataStorageConfig config;
    private final AlertRecordJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public JsonToPostgresMigration(DataStorageConfig config, AlertRecordJpaRepository jpaRepository) {
        this.config = config;
        this.jpaRepository = jpaRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void run(ApplicationArguments args) {
        File file = new File(config.getFilePath(FILENAME));
        if (!file.exists()) {
            return;
        }
        try {
            AlertRecord[] records = objectMapper.readValue(file, AlertRecord[].class);
            if (records != null && records.length > 0) {
                Arrays.stream(records).forEach(r -> {
                    String level = r.getLevel() != null ? r.getLevel().name() : null;
                    jpaRepository.save(new AlertRecordEntity(
                            r.getTitle(), r.getMessage(), level, r.getSource(), r.getReceivedAt()));
                });
                log.info("Migrated {} alert records from {} to PostgreSQL", records.length, FILENAME);
            }
            File migrated = new File(file.getParent(), FILENAME + ".migrated");
            if (file.renameTo(migrated)) {
                log.info("Renamed {} to {}.migrated", FILENAME, FILENAME);
            } else {
                log.warn("Could not rename {} after migration", FILENAME);
            }
        } catch (Exception e) {
            log.error("Failed to migrate {}: {}", FILENAME, e.getMessage());
        }
    }
}
