package com.openmc.webapp.repository;

import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ActivityTrackerSnapshotRepository Tests")
class ActivityTrackerSnapshotRepositoryTest {
    
    @Autowired
    private ActivityTrackerSnapshotRepository repository;
    
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }
    
    @Test
    @DisplayName("Should return empty list when no snapshots exist")
    void shouldReturnEmptyListWhenNoSnapshotsExist() {
        List<ActivityTrackerSnapshot> snapshots = repository.findAll();
        assertNotNull(snapshots);
        assertTrue(snapshots.isEmpty());
    }
    
    @Test
    @DisplayName("Should save and load snapshots successfully")
    void shouldSaveAndLoadSnapshotsSuccessfully() {
        List<ActivityTrackerSnapshot> snapshots = createTestSnapshots(3);
        
        repository.saveAll(snapshots);
        
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
    @DisplayName("Should delete all snapshots when deleteAll is called")
    void shouldDeleteAllSnapshotsWhenDeleteAllIsCalled() {
        List<ActivityTrackerSnapshot> snapshots = createTestSnapshots(2);
        repository.saveAll(snapshots);
        
        assertEquals(2, repository.findAll().size());
        
        repository.deleteAll();
        
        assertTrue(repository.findAll().isEmpty());
    }
    
    @Test
    @DisplayName("Should filter out snapshots older than cutoff using findByTimestampAfterOrderByTimestampDesc")
    void shouldFilterOutSnapshotsOlderThanCutoff() {
        ActivityTrackerStats stats = new ActivityTrackerStats(10, 50);
        
        // Recent snapshot (should be kept)
        repository.save(new ActivityTrackerSnapshot(
            Instant.now().minus(Duration.ofHours(12)), 
            stats, 
            Collections.emptyList(), 
            true
        ));
        
        // Old snapshot (should be filtered out)
        repository.save(new ActivityTrackerSnapshot(
            Instant.now().minus(Duration.ofDays(2)), 
            stats, 
            Collections.emptyList(), 
            true
        ));
        
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<ActivityTrackerSnapshot> filtered = repository.findByTimestampAfterOrderByTimestampDesc(cutoff);
        
        assertEquals(1, filtered.size());
    }
    
    @Test
    @DisplayName("Should preserve leaderboard data via cascade")
    void shouldPreserveLeaderboardData() {
        ActivityTrackerStats stats = new ActivityTrackerStats(5, 25);
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        leaderboard.add(new LeaderboardEntry("uuid1", "Player1", 10.5, 15));
        leaderboard.add(new LeaderboardEntry("uuid2", "Player2", 8.2, 12));
        
        ActivityTrackerSnapshot snapshot = new ActivityTrackerSnapshot(Instant.now(), stats, leaderboard, true);
        repository.save(snapshot);
        
        List<ActivityTrackerSnapshot> loadedSnapshots = repository.findAll();
        
        assertEquals(1, loadedSnapshots.size());
        ActivityTrackerSnapshot loaded = loadedSnapshots.get(0);
        assertEquals(2, loaded.getLeaderboard().size());
        assertEquals("Player1", loaded.getLeaderboard().get(0).getPlayerName());
        assertEquals(10.5, loaded.getLeaderboard().get(0).getHoursPlayed(), 0.01);
    }
    
    @Test
    @DisplayName("Should return snapshots ordered by timestamp descending")
    void shouldReturnSnapshotsOrderedByTimestampDescending() {
        ActivityTrackerStats stats = new ActivityTrackerStats(10, 50);
        
        Instant oldest = Instant.now().minus(Duration.ofHours(3));
        Instant middle = Instant.now().minus(Duration.ofHours(2));
        Instant newest = Instant.now().minus(Duration.ofHours(1));
        
        repository.save(new ActivityTrackerSnapshot(oldest, stats, Collections.emptyList(), true));
        repository.save(new ActivityTrackerSnapshot(newest, stats, Collections.emptyList(), true));
        repository.save(new ActivityTrackerSnapshot(middle, stats, Collections.emptyList(), true));
        
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<ActivityTrackerSnapshot> snapshots = repository.findByTimestampAfterOrderByTimestampDesc(cutoff);
        
        assertEquals(3, snapshots.size());
        assertTrue(snapshots.get(0).getTimestamp().isAfter(snapshots.get(1).getTimestamp()));
        assertTrue(snapshots.get(1).getTimestamp().isAfter(snapshots.get(2).getTimestamp()));
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
