package com.openmc.webapp.repository;

import com.openmc.webapp.model.DeploymentRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DeploymentRecordRepository Tests")
class DeploymentRecordRepositoryTest {

    @Autowired
    private DeploymentRecordRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should return empty list when no records exist")
    void shouldReturnEmptyListWhenNoRecordsExist() {
        List<DeploymentRecord> records = repository.findAll();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    @DisplayName("Should save and load records successfully")
    void shouldSaveAndLoadRecordsSuccessfully() {
        List<DeploymentRecord> records = createTestRecords(3);

        repository.saveAll(records);

        List<DeploymentRecord> loadedRecords = repository.findAll();

        assertEquals(3, loadedRecords.size());
        for (int i = 0; i < records.size(); i++) {
            assertEquals(records.get(i).getTimestamp(), loadedRecords.get(i).getTimestamp());
            assertEquals(records.get(i).getPluginName(), loadedRecords.get(i).getPluginName());
            assertEquals(records.get(i).getStatus(), loadedRecords.get(i).getStatus());
            assertEquals(records.get(i).getSource(), loadedRecords.get(i).getSource());
        }
    }

    @Test
    @DisplayName("Should delete all records when deleteAll is called")
    void shouldDeleteAllRecordsWhenDeleteAllIsCalled() {
        List<DeploymentRecord> records = createTestRecords(2);
        repository.saveAll(records);

        assertEquals(2, repository.findAll().size());

        repository.deleteAll();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Should filter out records older than cutoff using findByTimestampAfterOrderByTimestampDesc")
    void shouldFilterOutRecordsOlderThanCutoff() {
        // Recent record (should be kept)
        repository.save(new DeploymentRecord(
                Instant.now().minus(Duration.ofHours(12)), "RecentPlugin.jar",
                "SUCCESS", "automated", "main", null, "Deployed successfully"));

        // Old record (should be filtered out)
        repository.save(new DeploymentRecord(
                Instant.now().minus(Duration.ofDays(2)), "OldPlugin.jar",
                "SUCCESS", "automated", "main", null, "Deployed successfully"));

        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<DeploymentRecord> filtered = repository.findByTimestampAfterOrderByTimestampDesc(cutoff);

        assertEquals(1, filtered.size());
        assertEquals("RecentPlugin.jar", filtered.get(0).getPluginName());
    }

    @Test
    @DisplayName("Should return records ordered by timestamp descending")
    void shouldReturnRecordsOrderedByTimestampDescending() {
        Instant oldest = Instant.now().minus(Duration.ofHours(3));
        Instant middle = Instant.now().minus(Duration.ofHours(2));
        Instant newest = Instant.now().minus(Duration.ofHours(1));

        repository.save(new DeploymentRecord(oldest, "OldPlugin.jar", "SUCCESS",
                "automated", "main", null, "Old"));
        repository.save(new DeploymentRecord(newest, "NewPlugin.jar", "SUCCESS",
                "automated", "main", null, "New"));
        repository.save(new DeploymentRecord(middle, "MidPlugin.jar", "FAILURE",
                "webapp", "main", null, "Mid"));

        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<DeploymentRecord> records = repository.findByTimestampAfterOrderByTimestampDesc(cutoff);

        assertEquals(3, records.size());
        assertEquals("NewPlugin.jar", records.get(0).getPluginName());
        assertEquals("MidPlugin.jar", records.get(1).getPluginName());
        assertEquals("OldPlugin.jar", records.get(2).getPluginName());
    }

    private List<DeploymentRecord> createTestRecords(int count) {
        List<DeploymentRecord> records = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            records.add(new DeploymentRecord(
                    Instant.now().minus(Duration.ofMinutes(i * 30)),
                    "Plugin" + i + ".jar",
                    i % 2 == 0 ? "SUCCESS" : "FAILURE",
                    "automated",
                    "main",
                    "https://github.com/org/repo",
                    "Test deployment " + i));
        }

        return records;
    }
}
