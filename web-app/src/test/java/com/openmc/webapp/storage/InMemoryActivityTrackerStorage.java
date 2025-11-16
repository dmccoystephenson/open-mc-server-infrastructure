package com.openmc.webapp.storage;

import com.openmc.webapp.model.ActivityTrackerSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory implementation of ActivityTrackerStorage for testing purposes.
 */
public class InMemoryActivityTrackerStorage implements ActivityTrackerStorage {
    
    private List<ActivityTrackerSnapshot> snapshots = new ArrayList<>();
    
    @Override
    public void saveSnapshots(List<ActivityTrackerSnapshot> snapshots) {
        this.snapshots = new ArrayList<>(snapshots);
    }
    
    @Override
    public List<ActivityTrackerSnapshot> loadSnapshots() {
        return new ArrayList<>(snapshots);
    }
    
    @Override
    public void clear() {
        snapshots.clear();
    }
}
