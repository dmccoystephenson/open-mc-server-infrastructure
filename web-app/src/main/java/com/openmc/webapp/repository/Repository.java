package com.openmc.webapp.repository;

import java.util.List;

/**
 * Generic repository interface for data persistence.
 * Provides basic CRUD operations that can be extended for specific data types.
 * 
 * @param <T> The type of entity this repository manages
 */
public interface Repository<T> {
    
    /**
     * Save a list of entities to persistent storage
     * @param entities List of entities to save
     */
    void save(List<T> entities);
    
    /**
     * Load all entities from persistent storage
     * @return List of entities, or empty list if no data exists
     */
    List<T> findAll();
    
    /**
     * Clear all stored entities
     */
    void clear();
}
