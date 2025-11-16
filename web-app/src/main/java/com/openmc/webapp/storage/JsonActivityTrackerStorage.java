package com.openmc.webapp.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON-based implementation of ActivityTrackerStorage.
 * Stores Activity Tracker snapshots in a JSON file with configurable retention period.
 */
@Component
public class JsonActivityTrackerStorage implements ActivityTrackerStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonActivityTrackerStorage.class);
    private static final String DATA_FILE = "data/activity-tracker-history.json";
    private static final Duration DEFAULT_RETENTION_PERIOD = Duration.ofDays(7);
    
    private final ObjectMapper objectMapper;
    private final Duration retentionPeriod;
    private final File dataFile;
    
    public JsonActivityTrackerStorage() {
        this(DEFAULT_RETENTION_PERIOD);
    }
    
    public JsonActivityTrackerStorage(Duration retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        this.dataFile = new File(DATA_FILE);
        ensureDataDirectoryExists();
    }
    
    private void ensureDataDirectoryExists() {
        File dataDir = dataFile.getParentFile();
        if (dataDir != null && !dataDir.exists()) {
            if (dataDir.mkdirs()) {
                logger.info("Created data directory: {}", dataDir.getAbsolutePath());
            } else {
                logger.warn("Failed to create data directory: {}", dataDir.getAbsolutePath());
            }
        }
    }
    
    @Override
    public void saveSnapshots(List<ActivityTrackerSnapshot> snapshots) {
        try {
            // Filter out snapshots older than retention period
            Instant cutoff = Instant.now().minus(retentionPeriod);
            List<ActivityTrackerSnapshot> snapshotsToSave = snapshots.stream()
                    .filter(snapshot -> snapshot.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
            
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dataFile, snapshotsToSave);
            logger.debug("Saved {} Activity Tracker snapshots to {}", snapshotsToSave.size(), dataFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save Activity Tracker snapshots to {}: {}", dataFile.getAbsolutePath(), e.getMessage());
        }
    }
    
    @Override
    public List<ActivityTrackerSnapshot> loadSnapshots() {
        if (!dataFile.exists()) {
            logger.info("No existing Activity Tracker data file found at {}", dataFile.getAbsolutePath());
            return Collections.emptyList();
        }
        
        try {
            ActivityTrackerSnapshot[] snapshots = objectMapper.readValue(dataFile, ActivityTrackerSnapshot[].class);
            
            // Filter out snapshots older than retention period
            Instant cutoff = Instant.now().minus(retentionPeriod);
            List<ActivityTrackerSnapshot> validSnapshots = Arrays.stream(snapshots)
                    .filter(snapshot -> snapshot.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
            
            logger.info("Loaded {} Activity Tracker snapshots from {} (filtered from {})", 
                    validSnapshots.size(), dataFile.getAbsolutePath(), snapshots.length);
            return new ArrayList<>(validSnapshots);
        } catch (IOException e) {
            logger.error("Failed to load Activity Tracker snapshots from {}: {}", dataFile.getAbsolutePath(), e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public void clear() {
        if (dataFile.exists()) {
            if (dataFile.delete()) {
                logger.info("Cleared Activity Tracker data file: {}", dataFile.getAbsolutePath());
            } else {
                logger.warn("Failed to delete Activity Tracker data file: {}", dataFile.getAbsolutePath());
            }
        }
    }
}
