package com.openmc.webapp.repository;

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
    
    private static final String DATA_FILE = "data/activity-tracker-history.json";
    
    public ActivityTrackerSnapshotRepository() {
        super(DATA_FILE, ActivityTrackerSnapshot[].class);
    }
    
    public ActivityTrackerSnapshotRepository(Duration retentionPeriod) {
        super(DATA_FILE, ActivityTrackerSnapshot[].class, retentionPeriod);
    }
    
    @Override
    protected Instant getEntityTimestamp(ActivityTrackerSnapshot entity) {
        return entity.getTimestamp();
    }
}
