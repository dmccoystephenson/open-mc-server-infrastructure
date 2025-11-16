package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.repository.RetrievalRecordRepository;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RconService Persistence Tests")
class RconServicePersistenceTest {
    
    private static final String TEST_DATA_FILE = "data/retrieval-history.json";
    private ServerConfig serverConfig;
    private RetrievalRecordRepository repository;
    
    @BeforeEach
    void setUp() {
        cleanupDataFile();
        serverConfig = new ServerConfig();
        serverConfig.setRefreshIntervalMs(1); // Very short interval for testing
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
    @DisplayName("Should persist history across service restarts")
    void shouldPersistHistoryAcrossServiceRestarts() throws InterruptedException {
        // Create first service instance and generate some history
        RconService firstService = new RconService(serverConfig, repository);
        
        // Force multiple retrievals
        for (int i = 0; i < 3; i++) {
            Thread.sleep(5); // Ensure refresh interval passes
            firstService.getServerStatus();
        }
        
        // Get history from first service
        List<RetrievalRecord> firstHistory = firstService.getRetrievalHistory();
        assertEquals(3, firstHistory.size());
        
        // Create second service instance (simulating restart)
        RconService secondService = new RconService(serverConfig, repository);
        
        // Get history from second service
        List<RetrievalRecord> secondHistory = secondService.getRetrievalHistory();
        
        // History should be restored
        assertEquals(3, secondHistory.size());
        
        // Verify timestamps match
        for (int i = 0; i < firstHistory.size(); i++) {
            assertEquals(firstHistory.get(i).getTimestamp(), secondHistory.get(i).getTimestamp());
            assertEquals(firstHistory.get(i).isSuccess(), secondHistory.get(i).isSuccess());
            assertEquals(firstHistory.get(i).getPlayerCount(), secondHistory.get(i).getPlayerCount());
        }
    }
    
    @Test
    @DisplayName("Should start with empty history when no persisted data exists")
    void shouldStartWithEmptyHistoryWhenNoPersistedDataExists() {
        RconService service = new RconService(serverConfig, repository);
        
        List<RetrievalRecord> history = service.getRetrievalHistory();
        assertTrue(history.isEmpty());
    }
    
    @Test
    @DisplayName("Should limit loaded history to MAX_HISTORY_SIZE")
    void shouldLimitLoadedHistoryToMaxHistorySize() throws InterruptedException {
        // Create first service and generate more than MAX_HISTORY_SIZE records
        RconService firstService = new RconService(serverConfig, repository);
        
        // Force 15 retrievals (more than the limit of 10)
        for (int i = 0; i < 15; i++) {
            Thread.sleep(5); // Ensure refresh interval passes
            firstService.getServerStatus();
        }
        
        // Verify first service has 10 records
        assertEquals(10, firstService.getRetrievalHistory().size());
        
        // Create second service (simulating restart)
        RconService secondService = new RconService(serverConfig, repository);
        
        // Should load only 10 most recent records
        List<RetrievalRecord> history = secondService.getRetrievalHistory();
        assertEquals(10, history.size());
    }
    
    @Test
    @DisplayName("Should append new records to loaded history")
    void shouldAppendNewRecordsToLoadedHistory() throws InterruptedException {
        // Create first service and generate some history
        RconService firstService = new RconService(serverConfig, repository);
        firstService.getServerStatus();
        Thread.sleep(5);
        firstService.getServerStatus();
        
        assertEquals(2, firstService.getRetrievalHistory().size());
        
        // Create second service (simulating restart)
        RconService secondService = new RconService(serverConfig, repository);
        
        // Should have loaded 2 records
        assertEquals(2, secondService.getRetrievalHistory().size());
        
        // Add a new record
        Thread.sleep(5);
        secondService.getServerStatus();
        
        // Should now have 3 records
        assertEquals(3, secondService.getRetrievalHistory().size());
    }
}
