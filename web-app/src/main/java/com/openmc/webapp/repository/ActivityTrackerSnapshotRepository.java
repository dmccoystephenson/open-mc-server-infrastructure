package com.openmc.webapp.repository;

import com.openmc.webapp.config.DataStorageConfig;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Repository for persisting ActivityTrackerSnapshot entities.
 * Uses JSON file storage with a 7-day retention period.
 */
@Component
public class ActivityTrackerSnapshotRepository extends JsonRepository<ActivityTrackerSnapshot> {
    
    private static final String FILENAME = "activity-tracker-history.json";
    
    @org.springframework.beans.factory.annotation.Autowired
    public ActivityTrackerSnapshotRepository(DataStorageConfig config) {
        super(config.getFilePath(FILENAME), ActivityTrackerSnapshot[].class);
    }
    
    public ActivityTrackerSnapshotRepository(DataStorageConfig config, Duration retentionPeriod) {
        super(config.getFilePath(FILENAME), ActivityTrackerSnapshot[].class, retentionPeriod);
    }
    
    @Override
    protected Instant getEntityTimestamp(ActivityTrackerSnapshot entity) {
        return entity.getTimestamp();
    }
}
