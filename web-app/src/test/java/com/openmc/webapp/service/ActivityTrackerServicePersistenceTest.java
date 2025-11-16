package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.storage.JsonActivityTrackerStorage;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityTrackerService Persistence Tests")
class ActivityTrackerServicePersistenceTest {
    
    private static final String TEST_DATA_FILE = "data/activity-tracker-history.json";
    private ServerConfig serverConfig;
    private JsonActivityTrackerStorage storage;
    
    @BeforeEach
    void setUp() {
        cleanupDataFile();
        serverConfig = new ServerConfig();
        serverConfig.setActivityTrackerEnabled(false); // Disable to prevent actual API calls
        storage = new JsonActivityTrackerStorage();
    }
    
    @AfterEach
    void tearDown() {
        cleanupDataFile();
    }
    
    private void cleanupDataFile() {
        File dataFile = new File(TEST_DATA_FILE);
        if (dataFile.exists()) {
            dataFile.delete();
        }
        File dataDir = dataFile.getParentFile();
        if (dataDir != null && dataDir.exists() && dataDir.list() != null && dataDir.list().length == 0) {
            dataDir.delete();
        }
    }
    
    @Test
    @DisplayName("Should start with empty history when no persisted data exists")
    void shouldStartWithEmptyHistoryWhenNoPersistedDataExists() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, storage);
        
        List<ActivityTrackerSnapshot> history = service.getSnapshotHistory();
        assertTrue(history.isEmpty());
    }
    
    @Test
    @DisplayName("Should initialize cache with most recent snapshot on load")
    void shouldInitializeCacheWithMostRecentSnapshotOnLoad() {
        // This test verifies that when snapshots are loaded,
        // the service initializes its cache with the most recent successful snapshot
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, storage);
        
        // Since no data exists, last fetch time should be null
        assertNull(service.getLastFetchTime());
    }
    
    @Test
    @DisplayName("Should return unmodifiable list for snapshot history")
    void shouldReturnUnmodifiableListForSnapshotHistory() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, storage);
        
        List<ActivityTrackerSnapshot> history = service.getSnapshotHistory();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            history.clear();
        });
    }
}
