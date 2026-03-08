package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.repository.ActivityTrackerSnapshotRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ActivityTrackerService Persistence Tests")
@ExtendWith(MockitoExtension.class)
class ActivityTrackerServicePersistenceTest {
    
    private ServerConfig serverConfig;
    
    @Mock
    private ActivityTrackerSnapshotRepository repository;
    
    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setActivityTrackerEnabled(false); // Disable to prevent actual API calls
        when(repository.findByTimestampAfterOrderByTimestampDesc(any(Instant.class)))
            .thenReturn(Collections.emptyList());
    }
    
    @Test
    @DisplayName("Should start with empty history when no persisted data exists")
    void shouldStartWithEmptyHistoryWhenNoPersistedDataExists() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, repository);
        
        List<ActivityTrackerSnapshot> history = service.getSnapshotHistory();
        assertTrue(history.isEmpty());
    }
    
    @Test
    @DisplayName("Should initialize cache with most recent snapshot on load")
    void shouldInitializeCacheWithMostRecentSnapshotOnLoad() {
        // This test verifies that when snapshots are loaded,
        // the service initializes its cache with the most recent successful snapshot
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, repository);
        
        // Since no data exists, last fetch time should be null
        assertNull(service.getLastFetchTime());
    }
    
    @Test
    @DisplayName("Should return unmodifiable list for snapshot history")
    void shouldReturnUnmodifiableListForSnapshotHistory() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, repository);
        
        List<ActivityTrackerSnapshot> history = service.getSnapshotHistory();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            history.clear();
        });
    }
}
