package com.openmc.webapp.repository;

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
    
    private static final String DATA_FILE = "data/retrieval-history.json";
    
    public RetrievalRecordRepository() {
        super(DATA_FILE, RetrievalRecord[].class);
    }
    
    public RetrievalRecordRepository(Duration retentionPeriod) {
        super(DATA_FILE, RetrievalRecord[].class, retentionPeriod);
    }
    
    @Override
    protected Instant getEntityTimestamp(RetrievalRecord entity) {
        return entity.getTimestamp();
    }
}
