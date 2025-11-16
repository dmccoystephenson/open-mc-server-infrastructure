package com.openmc.webapp.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory implementation of Repository for testing purposes.
 * 
 * @param <T> The type of entity this repository manages
 */
public class InMemoryRepository<T> implements Repository<T> {
    
    private List<T> entities = new ArrayList<>();
    
    @Override
    public void save(List<T> entities) {
        this.entities = new ArrayList<>(entities);
    }
    
    @Override
    public List<T> findAll() {
        return new ArrayList<>(entities);
    }
    
    @Override
    public void clear() {
        entities.clear();
    }
}
