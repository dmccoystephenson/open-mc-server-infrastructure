package com.openmc.webapp.repository;

import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.service.RconService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetrievalRecordRepository Tests")
class RetrievalRecordRepositoryTest {
    
    private static final String TEST_DATA_FILE = "data/retrieval-history.json";
    private RetrievalRecordRepository repository;
    
    @BeforeEach
    void setUp() {
        cleanupDataFile();
        repository = new RetrievalRecordRepository();
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
        List<RetrievalRecord> records = repository.findAll();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }
    
    @Test
    @DisplayName("Should save and load records successfully")
    void shouldSaveAndLoadRecordsSuccessfully() {
        List<RetrievalRecord> records = createTestRecords(3);
        
        repository.save(records);
        
        List<RetrievalRecord> loadedRecords = repository.findAll();
        
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
        List<RetrievalRecord> records = createTestRecords(2);
        repository.save(records);
        
        File dataFile = new File(TEST_DATA_FILE);
        assertTrue(dataFile.exists());
        
        repository.clear();
        
        assertFalse(dataFile.exists());
    }
    
    @Test
    @DisplayName("Should filter out records older than retention period")
    void shouldFilterOutRecordsOlderThanRetentionPeriod() {
        RetrievalRecordRepository shortRetentionRepository = new RetrievalRecordRepository(Duration.ofDays(1));
        
        List<RetrievalRecord> records = new ArrayList<>();
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        // Recent record (should be kept)
        records.add(new RetrievalRecord(Instant.now().minus(Duration.ofHours(12)), true, 5, resourceUsage));
        
        // Old record (should be filtered out)
        records.add(new RetrievalRecord(Instant.now().minus(Duration.ofDays(2)), true, 3, resourceUsage));
        
        shortRetentionRepository.save(records);
        
        List<RetrievalRecord> loadedRecords = shortRetentionRepository.findAll();
        
        assertEquals(1, loadedRecords.size());
        assertEquals(5, loadedRecords.get(0).getPlayerCount());
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
