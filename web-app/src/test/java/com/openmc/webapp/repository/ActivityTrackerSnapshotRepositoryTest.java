package com.openmc.webapp.repository;

import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityTrackerSnapshotRepository Tests")
class ActivityTrackerSnapshotRepositoryTest {

    private Repository<ActivityTrackerSnapshot> repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository<>();
    }

    @Test
    @DisplayName("Should return empty list when no data exists")
    void shouldReturnEmptyListWhenNoDataFileExists() {
        List<ActivityTrackerSnapshot> snapshots = repository.findAll();
        assertNotNull(snapshots);
        assertTrue(snapshots.isEmpty());
    }

    @Test
    @DisplayName("Should save and load snapshots successfully")
    void shouldSaveAndLoadSnapshotsSuccessfully() {
        List<ActivityTrackerSnapshot> snapshots = createTestSnapshots(3);

        repository.save(snapshots);

        List<ActivityTrackerSnapshot> loadedSnapshots = repository.findAll();

        assertEquals(3, loadedSnapshots.size());
        for (int i = 0; i < snapshots.size(); i++) {
            assertEquals(snapshots.get(i).getTimestamp(), loadedSnapshots.get(i).getTimestamp());
            assertEquals(snapshots.get(i).isSuccess(), loadedSnapshots.get(i).isSuccess());
            if (snapshots.get(i).getStats() != null) {
                assertEquals(snapshots.get(i).getStats().getUniqueLogins(),
                    loadedSnapshots.get(i).getStats().getUniqueLogins());
            }
        }
    }

    @Test
    @DisplayName("Should clear data when clear is called")
    void shouldClearDataFileWhenClearIsCalled() {
        List<ActivityTrackerSnapshot> snapshots = createTestSnapshots(2);
        repository.save(snapshots);

        assertFalse(repository.findAll().isEmpty());

        repository.clear();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Should preserve leaderboard data")
    void shouldPreserveLeaderboardData() {
        ActivityTrackerStats stats = new ActivityTrackerStats(5, 25);
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        leaderboard.add(new LeaderboardEntry("uuid1", "Player1", 10.5, 15));
        leaderboard.add(new LeaderboardEntry("uuid2", "Player2", 8.2, 12));

        List<ActivityTrackerSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new ActivityTrackerSnapshot(Instant.now(), stats, leaderboard, true));

        repository.save(snapshots);
        List<ActivityTrackerSnapshot> loadedSnapshots = repository.findAll();

        assertEquals(1, loadedSnapshots.size());
        ActivityTrackerSnapshot loaded = loadedSnapshots.get(0);
        assertEquals(2, loaded.getLeaderboard().size());
        assertEquals("Player1", loaded.getLeaderboard().get(0).getPlayerName());
        assertEquals(10.5, loaded.getLeaderboard().get(0).getHoursPlayed(), 0.01);
    }

    private List<ActivityTrackerSnapshot> createTestSnapshots(int count) {
        List<ActivityTrackerSnapshot> snapshots = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ActivityTrackerStats stats = new ActivityTrackerStats(i * 10, i * 50);
            snapshots.add(new ActivityTrackerSnapshot(
                Instant.now().minus(Duration.ofMinutes(i * 30)),
                stats,
                Collections.emptyList(),
                true
            ));
        }

        return snapshots;
    }
}
