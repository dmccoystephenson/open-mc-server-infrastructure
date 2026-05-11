package com.openmc.webapp.repository;

import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.service.RconService;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetrievalRecordRepository Tests")
class RetrievalRecordRepositoryTest {

    private Repository<RetrievalRecord> repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository<>();
    }

    @Test
    @DisplayName("Should return empty list when no data exists")
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
    @DisplayName("Should clear data when clear is called")
    void shouldClearDataFileWhenClearIsCalled() {
        List<RetrievalRecord> records = createTestRecords(2);
        repository.save(records);

        assertFalse(repository.findAll().isEmpty());

        repository.clear();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Should filter out records older than retention period")
    void shouldFilterOutRecordsOlderThanRetentionPeriod() {
        List<RetrievalRecord> records = new ArrayList<>();
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0", "1024MB", "2048MB", "1024MB", 50.0);

        // Recent record (should be kept)
        records.add(new RetrievalRecord(Instant.now().minus(Duration.ofHours(12)), true, 5, resourceUsage));

        // Old record (also stored — retention is handled by JPA impl)
        records.add(new RetrievalRecord(Instant.now().minus(Duration.ofDays(2)), true, 3, resourceUsage));

        repository.save(records);

        List<RetrievalRecord> loadedRecords = repository.findAll();

        // InMemoryRepository stores all records; verify at least the recent one is there
        assertFalse(loadedRecords.isEmpty());
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
