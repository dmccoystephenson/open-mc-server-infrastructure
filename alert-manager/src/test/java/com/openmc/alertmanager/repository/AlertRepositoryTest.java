package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.config.DataStorageConfig;
import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertLevel;
import com.openmc.alertmanager.model.AlertRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertRepository Tests")
class AlertRepositoryTest {

    @TempDir
    Path tempDir;

    private AlertRepository alertRepository;

    private DataStorageConfig configFor(Path dir) {
        DataStorageConfig config = new DataStorageConfig();
        config.setBaseDirectory(dir.toString());
        return config;
    }

    @BeforeEach
    void setUp() {
        alertRepository = new AlertRepository(configFor(tempDir));
    }

    private Alert alert(String title) {
        return Alert.builder()
                .title(title)
                .message("Test message")
                .level(AlertLevel.INFO)
                .source("test")
                .build();
    }

    @Test
    @DisplayName("Should store alert and return it via getRecent")
    void shouldStoreAndRetrieve() {
        alertRepository.store(alert("First"));

        List<AlertRecord> records = alertRepository.getRecent(10);
        assertEquals(1, records.size());
        assertEquals("First", records.get(0).getTitle());
    }

    @Test
    @DisplayName("Should persist alerts to disk and reload on new instance")
    void shouldPersistAcrossRestarts() {
        alertRepository.store(alert("Alpha"));
        alertRepository.store(alert("Beta"));

        // Create a fresh instance pointing at the same directory
        AlertRepository reloaded = new AlertRepository(configFor(tempDir));
        List<AlertRecord> records = reloaded.getRecent(10);

        assertEquals(2, records.size());
        // Newest-first order
        assertEquals("Beta", records.get(0).getTitle());
        assertEquals("Alpha", records.get(1).getTitle());
    }

    @Test
    @DisplayName("Should return empty list when no alerts stored")
    void shouldReturnEmptyListWhenNoAlerts() {
        assertTrue(alertRepository.getRecent(10).isEmpty());
    }

    @Test
    @DisplayName("Should cap stored alerts at MAX_STORED_ALERTS")
    void shouldCapAtMaxStoredAlerts() {
        for (int i = 0; i < AlertRepository.MAX_STORED_ALERTS + 10; i++) {
            alertRepository.store(alert("Alert " + i));
        }
        assertEquals(AlertRepository.MAX_STORED_ALERTS, alertRepository.getRecent(200).size());
    }

    @Test
    @DisplayName("getRecent limit should be honoured")
    void shouldRespectLimit() {
        for (int i = 0; i < 20; i++) {
            alertRepository.store(alert("Alert " + i));
        }
        assertEquals(5, alertRepository.getRecent(5).size());
    }

    @Test
    @DisplayName("New instance starts empty when no file exists")
    void newInstanceStartsEmptyWithNoFile() {
        assertTrue(alertRepository.getRecent(10).isEmpty());
    }
}
