package com.openmc.webapp.storage;

import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.service.RconService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonDataStorage Tests")
class JsonDataStorageTest {
    
    private static final String TEST_DATA_FILE = "data/retrieval-history.json";
    private JsonDataStorage storage;
    
    @BeforeEach
    void setUp() {
        // Clean up before each test
        cleanupDataFile();
        storage = new JsonDataStorage();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up after each test
        cleanupDataFile();
    }
    
    private void cleanupDataFile() {
        File dataFile = new File(TEST_DATA_FILE);
        if (dataFile.exists()) {
            dataFile.delete();
        }
        // Also clean up the directory if it's empty
        File dataDir = dataFile.getParentFile();
        if (dataDir != null && dataDir.exists() && dataDir.list() != null && dataDir.list().length == 0) {
            dataDir.delete();
        }
    }
    
    @Test
    @DisplayName("Should return empty list when no data file exists")
    void shouldReturnEmptyListWhenNoDataFileExists() {
        List<RetrievalRecord> records = storage.loadRecords();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }
    
    @Test
    @DisplayName("Should save and load records successfully")
    void shouldSaveAndLoadRecordsSuccessfully() {
        // Create test records
        List<RetrievalRecord> records = createTestRecords(3);
        
        // Save records
        storage.saveRecords(records);
        
        // Load records
        List<RetrievalRecord> loadedRecords = storage.loadRecords();
        
        // Verify
        assertEquals(3, loadedRecords.size());
        for (int i = 0; i < records.size(); i++) {
            assertEquals(records.get(i).getTimestamp(), loadedRecords.get(i).getTimestamp());
            assertEquals(records.get(i).isSuccess(), loadedRecords.get(i).isSuccess());
            assertEquals(records.get(i).getPlayerCount(), loadedRecords.get(i).getPlayerCount());
        }
    }
    
    @Test
    @DisplayName("Should clear data file when clear is called")
    void shouldClearDataFileWhenClearIsCalled() {
        // Create and save test records
        List<RetrievalRecord> records = createTestRecords(2);
        storage.saveRecords(records);
        
        // Verify file exists
        File dataFile = new File(TEST_DATA_FILE);
        assertTrue(dataFile.exists());
        
        // Clear
        storage.clear();
        
        // Verify file is deleted
        assertFalse(dataFile.exists());
    }
    
    @Test
    @DisplayName("Should filter out records older than retention period")
    void shouldFilterOutRecordsOlderThanRetentionPeriod() {
        // Create storage with 1 day retention
        JsonDataStorage shortRetentionStorage = new JsonDataStorage(Duration.ofDays(1));
        
        // Create records with different timestamps
        List<RetrievalRecord> records = new ArrayList<>();
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        // Recent record (should be kept)
        records.add(new RetrievalRecord(Instant.now().minus(Duration.ofHours(12)), true, 5, resourceUsage));
        
        // Old record (should be filtered out)
        records.add(new RetrievalRecord(Instant.now().minus(Duration.ofDays(2)), true, 3, resourceUsage));
        
        // Save records
        shortRetentionStorage.saveRecords(records);
        
        // Load records
        List<RetrievalRecord> loadedRecords = shortRetentionStorage.loadRecords();
        
        // Only the recent record should be loaded
        assertEquals(1, loadedRecords.size());
        assertEquals(5, loadedRecords.get(0).getPlayerCount());
    }
    
    @Test
    @DisplayName("Should handle empty record list")
    void shouldHandleEmptyRecordList() {
        List<RetrievalRecord> records = new ArrayList<>();
        storage.saveRecords(records);
        
        List<RetrievalRecord> loadedRecords = storage.loadRecords();
        assertTrue(loadedRecords.isEmpty());
    }
    
    @Test
    @DisplayName("Should preserve ResourceUsage data")
    void shouldPreserveResourceUsageData() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage(
            "20.0, 19.8, 19.9", 
            "1536MB", 
            "2048MB", 
            "512MB", 
            75.0
        );
        
        List<RetrievalRecord> records = new ArrayList<>();
        records.add(new RetrievalRecord(Instant.now(), true, 10, resourceUsage));
        
        storage.saveRecords(records);
        List<RetrievalRecord> loadedRecords = storage.loadRecords();
        
        assertEquals(1, loadedRecords.size());
        RconService.ResourceUsage loadedUsage = loadedRecords.get(0).getResourceUsage();
        assertEquals("20.0, 19.8, 19.9", loadedUsage.getTps());
        assertEquals("1536MB", loadedUsage.getMemoryUsed());
        assertEquals("2048MB", loadedUsage.getMemoryMax());
        assertEquals("512MB", loadedUsage.getMemoryFree());
        assertEquals(75.0, loadedUsage.getMemoryUsedPercent(), 0.01);
    }
    
    private List<RetrievalRecord> createTestRecords(int count) {
        List<RetrievalRecord> records = new ArrayList<>();
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        for (int i = 0; i < count; i++) {
            records.add(new RetrievalRecord(
                Instant.now().minus(Duration.ofMinutes(i * 30)),
                true,
                i,
                resourceUsage
            ));
        }
        
        return records;
    }
}
