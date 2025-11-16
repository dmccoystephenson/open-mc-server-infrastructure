package com.openmc.webapp.storage;

import com.openmc.webapp.model.ActivityTrackerSnapshot;
import java.util.List;

/**
 * Interface for storing and retrieving Activity Tracker data persistence.
 */
public interface ActivityTrackerStorage {
    
    /**
     * Save Activity Tracker snapshots to persistent storage
     * @param snapshots List of snapshots to save
     */
    void saveSnapshots(List<ActivityTrackerSnapshot> snapshots);
    
    /**
     * Load Activity Tracker snapshots from persistent storage
     * @return List of snapshots, or empty list if no data exists
     */
    List<ActivityTrackerSnapshot> loadSnapshots();
    
    /**
     * Clear all stored snapshots
     */
    void clear();
}
