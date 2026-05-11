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
import java.util.Collections;
import java.util.List;

@Component
public class JsonToPostgresMigration implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(JsonToPostgresMigration.class);

    private final DataStorageConfig config;
    private final ActivityTrackerSnapshotRepository snapshotRepo;
    private final DeploymentRecordRepository deploymentRepo;
    private final RetrievalRecordRepository retrievalRepo;
    private final ObjectMapper objectMapper;

    public JsonToPostgresMigration(DataStorageConfig config,
                                   ActivityTrackerSnapshotRepository snapshotRepo,
                                   DeploymentRecordRepository deploymentRepo,
                                   RetrievalRecordRepository retrievalRepo) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
        this.deploymentRepo = deploymentRepo;
        this.retrievalRepo = retrievalRepo;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateFile("activity-tracker-history.json", ActivityTrackerSnapshot[].class, snapshotRepo::save);
        migrateFile("deployment-history.json", DeploymentRecord[].class, deploymentRepo::save);
        migrateFile("retrieval-history.json", RetrievalRecord[].class, retrievalRepo::save);
    }

    private <T> void migrateFile(String filename, Class<T[]> arrayClass,
                                  java.util.function.Consumer<List<T>> saveAll) {
        File file = new File(config.getFilePath(filename));
        if (!file.exists()) {
            return;
        }
        try {
            T[] records = objectMapper.readValue(file, arrayClass);
            List<T> list = records != null ? Arrays.asList(records) : Collections.emptyList();
            if (!list.isEmpty()) {
                saveAll.accept(list);
                logger.info("Migrated {} records from {} to PostgreSQL", list.size(), filename);
            }
            File migrated = new File(file.getParent(), filename + ".migrated");
            if (file.renameTo(migrated)) {
                logger.info("Renamed {} to {}", filename, filename + ".migrated");
            } else {
                logger.warn("Could not rename {} after migration", filename);
            }
        } catch (Exception e) {
            logger.error("Failed to migrate {}: {}", filename, e.getMessage());
        }
    }
}
