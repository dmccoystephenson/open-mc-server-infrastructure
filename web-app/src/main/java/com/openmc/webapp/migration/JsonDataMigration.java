package com.openmc.webapp.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.webapp.config.DataStorageConfig;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.model.DeploymentRecord;
import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.repository.ActivityTrackerSnapshotRepository;
import com.openmc.webapp.repository.DeploymentRecordRepository;
import com.openmc.webapp.repository.RetrievalRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

/**
 * One-time migration runner that reads existing JSON data files and inserts them
 * into the PostgreSQL database. Idempotent: skips migration if the database already
 * contains records for that entity type.
 */
@Component
public class JsonDataMigration implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(JsonDataMigration.class);

    private final DataStorageConfig dataStorageConfig;
    private final RetrievalRecordRepository retrievalRecordRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final ActivityTrackerSnapshotRepository activityTrackerSnapshotRepository;
    private final ObjectMapper objectMapper;

    public JsonDataMigration(DataStorageConfig dataStorageConfig,
                             RetrievalRecordRepository retrievalRecordRepository,
                             DeploymentRecordRepository deploymentRecordRepository,
                             ActivityTrackerSnapshotRepository activityTrackerSnapshotRepository) {
        this.dataStorageConfig = dataStorageConfig;
        this.retrievalRecordRepository = retrievalRecordRepository;
        this.deploymentRecordRepository = deploymentRecordRepository;
        this.activityTrackerSnapshotRepository = activityTrackerSnapshotRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateRetrievalRecords();
        migrateDeploymentRecords();
        migrateActivityTrackerSnapshots();
    }

    private void migrateRetrievalRecords() {
        if (retrievalRecordRepository.existsByIdIsNotNull()) {
            logger.info("Retrieval records already exist in database, skipping JSON migration");
            return;
        }

        File file = new File(dataStorageConfig.getFilePath("retrieval-history.json"));
        if (!file.exists()) {
            logger.info("No retrieval-history.json file found, skipping migration");
            return;
        }

        try {
            RetrievalRecord[] records = objectMapper.readValue(file, RetrievalRecord[].class);
            retrievalRecordRepository.saveAll(Arrays.asList(records));
            logger.info("Migrated {} retrieval records from JSON to database", records.length);
        } catch (Exception e) {
            logger.error("Failed to migrate retrieval records from JSON", e);
        }
    }

    private void migrateDeploymentRecords() {
        if (deploymentRecordRepository.existsByIdIsNotNull()) {
            logger.info("Deployment records already exist in database, skipping JSON migration");
            return;
        }

        File file = new File(dataStorageConfig.getFilePath("deployment-history.json"));
        if (!file.exists()) {
            logger.info("No deployment-history.json file found, skipping migration");
            return;
        }

        try {
            DeploymentRecord[] records = objectMapper.readValue(file, DeploymentRecord[].class);
            deploymentRecordRepository.saveAll(Arrays.asList(records));
            logger.info("Migrated {} deployment records from JSON to database", records.length);
        } catch (Exception e) {
            logger.error("Failed to migrate deployment records from JSON", e);
        }
    }

    private void migrateActivityTrackerSnapshots() {
        if (activityTrackerSnapshotRepository.existsByIdIsNotNull()) {
            logger.info("Activity tracker snapshots already exist in database, skipping JSON migration");
            return;
        }

        File file = new File(dataStorageConfig.getFilePath("activity-tracker-history.json"));
        if (!file.exists()) {
            logger.info("No activity-tracker-history.json file found, skipping migration");
            return;
        }

        try {
            ActivityTrackerSnapshot[] snapshots = objectMapper.readValue(file, ActivityTrackerSnapshot[].class);
            activityTrackerSnapshotRepository.saveAll(Arrays.asList(snapshots));
            logger.info("Migrated {} activity tracker snapshots from JSON to database", snapshots.length);
        } catch (Exception e) {
            logger.error("Failed to migrate activity tracker snapshots from JSON", e);
        }
    }
}
