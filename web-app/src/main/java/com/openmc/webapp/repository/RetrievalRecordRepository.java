package com.openmc.webapp.repository;

import com.openmc.webapp.config.DataStorageConfig;
import com.openmc.webapp.model.RetrievalRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Repository for persisting RetrievalRecord entities.
 * Uses JSON file storage with a 7-day retention period.
 */
@Component
public class RetrievalRecordRepository extends JsonRepository<RetrievalRecord> {
    
    private static final String FILENAME = "retrieval-history.json";
    
    @org.springframework.beans.factory.annotation.Autowired
    public RetrievalRecordRepository(DataStorageConfig config) {
        super(config.getFilePath(FILENAME), RetrievalRecord[].class);
    }
    
    public RetrievalRecordRepository(DataStorageConfig config, Duration retentionPeriod) {
        super(config.getFilePath(FILENAME), RetrievalRecord[].class, retentionPeriod);
    }
    
    @Override
    protected Instant getEntityTimestamp(RetrievalRecord entity) {
        return entity.getTimestamp();
    }
}
