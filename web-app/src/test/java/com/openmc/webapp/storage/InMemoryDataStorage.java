package com.openmc.webapp.storage;

import com.openmc.webapp.model.RetrievalRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory implementation of DataStorage for testing purposes.
 */
public class InMemoryDataStorage implements DataStorage {
    
    private List<RetrievalRecord> records = new ArrayList<>();
    
    @Override
    public void saveRecords(List<RetrievalRecord> records) {
        this.records = new ArrayList<>(records);
    }
    
    @Override
    public List<RetrievalRecord> loadRecords() {
        return new ArrayList<>(records);
    }
    
    @Override
    public void clear() {
        records.clear();
    }
}
