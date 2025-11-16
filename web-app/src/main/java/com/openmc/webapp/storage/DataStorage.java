package com.openmc.webapp.storage;

import com.openmc.webapp.model.RetrievalRecord;
import java.util.List;

/**
 * Interface for storing and retrieving data persistence.
 * Abstracts the storage mechanism to allow different implementations.
 */
public interface DataStorage {
    
    /**
     * Save retrieval records to persistent storage
     * @param records List of records to save
     */
    void saveRecords(List<RetrievalRecord> records);
    
    /**
     * Load retrieval records from persistent storage
     * @return List of records, or empty list if no data exists
     */
    List<RetrievalRecord> loadRecords();
    
    /**
     * Clear all stored records
     */
    void clear();
}
