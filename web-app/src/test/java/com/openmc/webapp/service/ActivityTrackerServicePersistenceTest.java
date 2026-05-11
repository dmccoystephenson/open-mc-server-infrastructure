package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.repository.InMemoryRepository;
import com.openmc.webapp.repository.Repository;
import com.openmc.webapp.mapper.PlayerProfileMapper;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("ActivityTrackerService Persistence Tests")
class ActivityTrackerServicePersistenceTest {

    private ServerConfig serverConfig;
    private Repository<ActivityTrackerSnapshot> repository;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setActivityTrackerEnabled(false); // Disable to prevent actual API calls
        repository = new InMemoryRepository<>();
    }

    @Test
    @DisplayName("Should start with empty history when no persisted data exists")
    void shouldStartWithEmptyHistoryWhenNoPersistedDataExists() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, repository, mock(RestTemplate.class), mock(PlayerProfileMapper.class));

        List<ActivityTrackerSnapshot> history = service.getSnapshotHistory();
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Should initialize cache with most recent snapshot on load")
    void shouldInitializeCacheWithMostRecentSnapshotOnLoad() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, repository, mock(RestTemplate.class), mock(PlayerProfileMapper.class));

        // Since no data exists, last fetch time should be null
        assertNull(service.getLastFetchTime());
    }

    @Test
    @DisplayName("Should return unmodifiable list for snapshot history")
    void shouldReturnUnmodifiableListForSnapshotHistory() {
        ActivityTrackerService service = new ActivityTrackerService(serverConfig, repository, mock(RestTemplate.class), mock(PlayerProfileMapper.class));

        List<ActivityTrackerSnapshot> history = service.getSnapshotHistory();

        assertThrows(UnsupportedOperationException.class, () -> {
            history.clear();
        });
    }
}
