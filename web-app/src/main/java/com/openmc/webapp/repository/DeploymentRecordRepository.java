package com.openmc.webapp.repository;

import com.openmc.webapp.config.DataStorageConfig;
import com.openmc.webapp.model.DeploymentRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Repository for persisting DeploymentRecord entities.
 * Uses JSON file storage with a 7-day retention period.
 */
@Component
public class DeploymentRecordRepository extends JsonRepository<DeploymentRecord> {

    private static final String FILENAME = "deployment-history.json";

    @org.springframework.beans.factory.annotation.Autowired
    public DeploymentRecordRepository(DataStorageConfig config) {
        super(config.getFilePath(FILENAME), DeploymentRecord[].class);
    }

    public DeploymentRecordRepository(DataStorageConfig config, Duration retentionPeriod) {
        super(config.getFilePath(FILENAME), DeploymentRecord[].class, retentionPeriod);
    }

    @Override
    protected Instant getEntityTimestamp(DeploymentRecord entity) {
        return entity.getTimestamp();
    }
}
