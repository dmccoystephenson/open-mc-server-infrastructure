package com.openmc.webapp.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.webapp.model.RetrievalRecord;
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
 * JSON-based implementation of DataStorage.
 * Stores retrieval records in a JSON file with configurable retention period.
 */
@Component
public class JsonDataStorage implements DataStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonDataStorage.class);
    private static final String DATA_FILE = "data/retrieval-history.json";
    private static final Duration DEFAULT_RETENTION_PERIOD = Duration.ofDays(7);
    
    private final ObjectMapper objectMapper;
    private final Duration retentionPeriod;
    private final File dataFile;
    
    public JsonDataStorage() {
        this(DEFAULT_RETENTION_PERIOD);
    }
    
    public JsonDataStorage(Duration retentionPeriod) {
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
    public void saveRecords(List<RetrievalRecord> records) {
        try {
            // Filter out records older than retention period
            Instant cutoff = Instant.now().minus(retentionPeriod);
            List<RetrievalRecord> recordsToSave = records.stream()
                    .filter(record -> record.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
            
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dataFile, recordsToSave);
            logger.debug("Saved {} records to {}", recordsToSave.size(), dataFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save records to {}: {}", dataFile.getAbsolutePath(), e.getMessage());
        }
    }
    
    @Override
    public List<RetrievalRecord> loadRecords() {
        if (!dataFile.exists()) {
            logger.info("No existing data file found at {}", dataFile.getAbsolutePath());
            return Collections.emptyList();
        }
        
        try {
            RetrievalRecord[] records = objectMapper.readValue(dataFile, RetrievalRecord[].class);
            
            // Filter out records older than retention period
            Instant cutoff = Instant.now().minus(retentionPeriod);
            List<RetrievalRecord> validRecords = Arrays.stream(records)
                    .filter(record -> record.getTimestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
            
            logger.info("Loaded {} records from {} (filtered from {})", 
                    validRecords.size(), dataFile.getAbsolutePath(), records.length);
            return new ArrayList<>(validRecords);
        } catch (IOException e) {
            logger.error("Failed to load records from {}: {}", dataFile.getAbsolutePath(), e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public void clear() {
        if (dataFile.exists()) {
            if (dataFile.delete()) {
                logger.info("Cleared data file: {}", dataFile.getAbsolutePath());
            } else {
                logger.warn("Failed to delete data file: {}", dataFile.getAbsolutePath());
            }
        }
    }
}
