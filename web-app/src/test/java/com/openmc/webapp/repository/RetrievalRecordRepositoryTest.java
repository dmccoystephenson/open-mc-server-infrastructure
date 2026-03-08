package com.openmc.webapp.repository;

import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.service.RconService;
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
@DisplayName("RetrievalRecordRepository Tests")
class RetrievalRecordRepositoryTest {
    
    @Autowired
    private RetrievalRecordRepository repository;
    
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }
    
    @Test
    @DisplayName("Should return empty list when no records exist")
    void shouldReturnEmptyListWhenNoRecordsExist() {
        List<RetrievalRecord> records = repository.findAll();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }
    
    @Test
    @DisplayName("Should save and load records successfully")
    void shouldSaveAndLoadRecordsSuccessfully() {
        List<RetrievalRecord> records = createTestRecords(3);
        
        repository.saveAll(records);
        
        List<RetrievalRecord> loadedRecords = repository.findAll();
        
        assertEquals(3, loadedRecords.size());
        for (int i = 0; i < records.size(); i++) {
            assertEquals(records.get(i).getTimestamp(), loadedRecords.get(i).getTimestamp());
            assertEquals(records.get(i).isSuccess(), loadedRecords.get(i).isSuccess());
            assertEquals(records.get(i).getPlayerCount(), loadedRecords.get(i).getPlayerCount());
        }
    }
    
    @Test
    @DisplayName("Should delete all records when deleteAll is called")
    void shouldDeleteAllRecordsWhenDeleteAllIsCalled() {
        List<RetrievalRecord> records = createTestRecords(2);
        repository.saveAll(records);
        
        assertEquals(2, repository.findAll().size());
        
        repository.deleteAll();
        
        assertTrue(repository.findAll().isEmpty());
    }
    
    @Test
    @DisplayName("Should filter out records older than cutoff using findByTimestampAfterOrderByTimestampDesc")
    void shouldFilterOutRecordsOlderThanCutoff() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        // Recent record (should be kept)
        repository.save(new RetrievalRecord(Instant.now().minus(Duration.ofHours(12)), true, 5, resourceUsage));
        
        // Old record (should be filtered out)
        repository.save(new RetrievalRecord(Instant.now().minus(Duration.ofDays(2)), true, 3, resourceUsage));
        
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<RetrievalRecord> filtered = repository.findByTimestampAfterOrderByTimestampDesc(cutoff);
        
        assertEquals(1, filtered.size());
        assertEquals(5, filtered.get(0).getPlayerCount());
    }
    
    @Test
    @DisplayName("Should return records ordered by timestamp descending")
    void shouldReturnRecordsOrderedByTimestampDescending() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        Instant oldest = Instant.now().minus(Duration.ofHours(3));
        Instant middle = Instant.now().minus(Duration.ofHours(2));
        Instant newest = Instant.now().minus(Duration.ofHours(1));
        
        repository.save(new RetrievalRecord(oldest, true, 1, resourceUsage));
        repository.save(new RetrievalRecord(newest, true, 3, resourceUsage));
        repository.save(new RetrievalRecord(middle, true, 2, resourceUsage));
        
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        List<RetrievalRecord> records = repository.findByTimestampAfterOrderByTimestampDesc(cutoff);
        
        assertEquals(3, records.size());
        assertEquals(3, records.get(0).getPlayerCount());
        assertEquals(2, records.get(1).getPlayerCount());
        assertEquals(1, records.get(2).getPlayerCount());
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
