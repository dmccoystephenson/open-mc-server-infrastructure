package com.openmc.webapp.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Abstract base class for JSON-based repository implementations.
 * Provides common functionality for persisting entities to JSON files with retention periods.
 * 
 * @param <T> The type of entity this repository manages
 */
public abstract class JsonRepository<T> implements Repository<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRepository.class);
    private static final Duration DEFAULT_RETENTION_PERIOD = Duration.ofDays(7);
    
    private final ObjectMapper objectMapper;
    private final Duration retentionPeriod;
    private final File dataFile;
    private final Class<T[]> arrayClass;
    
    /**
     * Constructor with default retention period
     * @param filePath Path to the JSON file for persistence
     * @param arrayClass Class object for the array type (used for deserialization)
     */
    protected JsonRepository(String filePath, Class<T[]> arrayClass) {
        this(filePath, arrayClass, DEFAULT_RETENTION_PERIOD);
    }
    
    /**
     * Constructor with custom retention period
     * @param filePath Path to the JSON file for persistence
     * @param arrayClass Class object for the array type (used for deserialization)
     * @param retentionPeriod How long to retain entities
     */
    protected JsonRepository(String filePath, Class<T[]> arrayClass, Duration retentionPeriod) {
        this.arrayClass = arrayClass;
        this.retentionPeriod = retentionPeriod;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        this.dataFile = new File(filePath);
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
    
    /**
     * Extract timestamp from an entity for retention filtering.
     * Subclasses must implement this to support retention filtering.
     * 
     * @param entity The entity to extract timestamp from
     * @return The timestamp of the entity
     */
    protected abstract Instant getEntityTimestamp(T entity);
    
    @Override
    public void save(List<T> entities) {
        Instant cutoff = Instant.now().minus(retentionPeriod);
        List<T> entitiesToSave = entities.stream()
                .filter(entity -> getEntityTimestamp(entity).isAfter(cutoff))
                .collect(Collectors.toList());

        // Write to a sibling temp file first, then rename atomically so a crash
        // mid-write never leaves the data file partially written.
        Path target = dataFile.toPath();
        Path tmp = null;
        try {
            tmp = Files.createTempFile(target.getParent(), ".json-repo-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), entitiesToSave);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null; // successfully moved; skip deletion in finally
            logger.debug("Saved {} entities to {}", entitiesToSave.size(), dataFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save entities to {}: {}", dataFile.getAbsolutePath(), e.getMessage());
            throw new UncheckedIOException("Failed to save entities to " + dataFile.getAbsolutePath(), e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }
    
    @Override
    public List<T> findAll() {
        if (!dataFile.exists()) {
            logger.info("No existing data file found at {}", dataFile.getAbsolutePath());
            return Collections.emptyList();
        }
        
        try {
            T[] entities = objectMapper.readValue(dataFile, arrayClass);
            
            // Filter out entities older than retention period
            Instant cutoff = Instant.now().minus(retentionPeriod);
            List<T> validEntities = Arrays.stream(entities)
                    .filter(entity -> getEntityTimestamp(entity).isAfter(cutoff))
                    .collect(Collectors.toList());
            
            logger.info("Loaded {} entities from {} (filtered from {})", 
                    validEntities.size(), dataFile.getAbsolutePath(), entities.length);
            return new ArrayList<>(validEntities);
        } catch (IOException e) {
            logger.error("Failed to load entities from {}: {}", dataFile.getAbsolutePath(), e.getMessage());
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
    
    /**
     * Get the retention period for this repository
     * @return The retention period
     */
    protected Duration getRetentionPeriod() {
        return retentionPeriod;
    }
    
    /**
     * Get the data file path
     * @return The file path
     */
    protected String getFilePath() {
        return dataFile.getAbsolutePath();
    }
}
