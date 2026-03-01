package com.openmc.alertmanager.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.alertmanager.config.DataStorageConfig;
import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Persistent store for recent alerts.
 * Retains up to {@link #MAX_STORED_ALERTS} most recent entries (newest first) in memory
 * and flushes the full list to a JSON file on every write so that alerts survive restarts.
 */
@Slf4j
@Component
public class AlertRepository {

    public static final int MAX_STORED_ALERTS = 100;
    private static final String FILENAME = "alert-history.json";

    private final Deque<AlertRecord> recentAlerts = new ConcurrentLinkedDeque<>();
    private final ObjectMapper objectMapper;
    private final File dataFile;

    public AlertRepository(DataStorageConfig config) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.dataFile = new File(config.getFilePath(FILENAME));
        ensureDataDirectory();
        loadFromFile();
    }

    /**
     * Store an alert in memory and persist the updated list to disk.
     *
     * @param alert the alert to store
     */
    public void store(Alert alert) {
        recentAlerts.addFirst(AlertRecord.builder()
                .title(alert.getTitle())
                .message(alert.getMessage())
                .level(alert.getLevel())
                .source(alert.getSource())
                .receivedAt(Instant.now())
                .build());
        // Trim to max size
        while (recentAlerts.size() > MAX_STORED_ALERTS) {
            recentAlerts.removeLast();
        }
        persistToFile();
    }

    /**
     * Return the most recent alerts, newest first.
     *
     * @param limit maximum number of records to return
     * @return list of recent alert records
     */
    public List<AlertRecord> getRecent(int limit) {
        List<AlertRecord> result = new ArrayList<>();
        int count = 0;
        for (AlertRecord record : recentAlerts) {
            if (count >= limit) {
                break;
            }
            result.add(record);
            count++;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void ensureDataDirectory() {
        File dir = dataFile.getParentFile();
        if (dir != null && !dir.exists()) {
            if (dir.mkdirs()) {
                log.info("Created data directory: {}", dir.getAbsolutePath());
            } else {
                log.warn("Failed to create data directory: {}", dir.getAbsolutePath());
            }
        }
    }

    private void loadFromFile() {
        if (!dataFile.exists()) {
            log.info("No existing alert history file found at {}", dataFile.getAbsolutePath());
            return;
        }
        try {
            AlertRecord[] records = objectMapper.readValue(dataFile, AlertRecord[].class);
            // File stores newest-first, matching the deque ordering.
            recentAlerts.addAll(Arrays.asList(records));
            // Honour the cap in case the file was written by an older version with a different limit.
            while (recentAlerts.size() > MAX_STORED_ALERTS) {
                recentAlerts.removeLast();
            }
            log.info("Loaded {} alert records from {}", recentAlerts.size(), dataFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to load alert history from {}: {}", dataFile.getAbsolutePath(), e.getMessage());
        }
    }

    private void persistToFile() {
        try {
            List<AlertRecord> snapshot = new ArrayList<>(recentAlerts);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, snapshot);
            log.debug("Persisted {} alert records to {}", snapshot.size(), dataFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to persist alert history to {}: {}", dataFile.getAbsolutePath(), e.getMessage());
        }
    }
}
