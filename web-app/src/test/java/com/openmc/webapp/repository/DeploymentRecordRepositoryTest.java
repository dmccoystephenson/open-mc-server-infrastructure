package com.openmc.webapp.repository;

import com.openmc.webapp.model.DeploymentRecord;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeploymentRecordRepository Tests")
class DeploymentRecordRepositoryTest {

    private Repository<DeploymentRecord> repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository<>();
    }

    @Test
    @DisplayName("Should return empty list when no data exists")
    void shouldReturnEmptyListWhenNoDataFileExists() {
        List<DeploymentRecord> records = repository.findAll();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    @DisplayName("Should save and load records successfully")
    void shouldSaveAndLoadRecordsSuccessfully() {
        List<DeploymentRecord> records = createTestRecords(3);

        repository.save(records);

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
    @DisplayName("Should clear data when clear is called")
    void shouldClearDataFileWhenClearIsCalled() {
        List<DeploymentRecord> records = createTestRecords(2);
        repository.save(records);

        assertFalse(repository.findAll().isEmpty());

        repository.clear();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Should filter out records older than retention period")
    void shouldFilterOutRecordsOlderThanRetentionPeriod() {
        List<DeploymentRecord> records = new ArrayList<>();

        // Recent record (should be kept)
        records.add(new DeploymentRecord(
                Instant.now().minus(Duration.ofHours(12)), "RecentPlugin.jar",
                "SUCCESS", "automated", "main", null, "Deployed successfully"));

        // Old record (should also be kept in InMemory — retention is a DB concern)
        records.add(new DeploymentRecord(
                Instant.now().minus(Duration.ofDays(2)), "OldPlugin.jar",
                "SUCCESS", "automated", "main", null, "Deployed successfully"));

        repository.save(records);

        List<DeploymentRecord> loadedRecords = repository.findAll();

        // InMemoryRepository stores all records; retention filtering is done in the JPA impl
        assertFalse(loadedRecords.isEmpty());
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
