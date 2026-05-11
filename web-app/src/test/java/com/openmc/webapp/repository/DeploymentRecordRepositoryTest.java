package com.openmc.webapp.repository;

import com.openmc.webapp.config.TestDataStorageConfig;
import com.openmc.webapp.model.DeploymentRecord;
import org.junit.jupiter.api.*;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeploymentRecordRepository Tests")
class DeploymentRecordRepositoryTest {

    private static final String TEST_DATA_FILE = "data/deployment-history.json";
    private DeploymentRecordRepository repository;
    private TestDataStorageConfig config;

    @BeforeEach
    void setUp() {
        cleanupDataFile();
        config = new TestDataStorageConfig();
        repository = new DeploymentRecordRepository(config);
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
    @DisplayName("Should return empty list when no data file exists")
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
    @DisplayName("Should clear data file when clear is called")
    void shouldClearDataFileWhenClearIsCalled() {
        List<DeploymentRecord> records = createTestRecords(2);
        repository.save(records);

        File dataFile = new File(TEST_DATA_FILE);
        assertTrue(dataFile.exists());

        repository.clear();

        assertFalse(dataFile.exists());
    }

    @Test
    @DisplayName("Should filter out records older than retention period")
    void shouldFilterOutRecordsOlderThanRetentionPeriod() {
        DeploymentRecordRepository shortRetentionRepository = new DeploymentRecordRepository(config, Duration.ofDays(1));

        List<DeploymentRecord> records = new ArrayList<>();

        // Recent record (should be kept)
        records.add(new DeploymentRecord(
                Instant.now().minus(Duration.ofHours(12)), "RecentPlugin.jar",
                "SUCCESS", "automated", "main", null, "Deployed successfully"));

        // Old record (should be filtered out)
        records.add(new DeploymentRecord(
                Instant.now().minus(Duration.ofDays(2)), "OldPlugin.jar",
                "SUCCESS", "automated", "main", null, "Deployed successfully"));

        shortRetentionRepository.save(records);

        List<DeploymentRecord> loadedRecords = shortRetentionRepository.findAll();

        assertEquals(1, loadedRecords.size());
        assertEquals("RecentPlugin.jar", loadedRecords.get(0).getPluginName());
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
