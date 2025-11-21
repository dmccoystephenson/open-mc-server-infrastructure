package com.openmc.webapp.config;

/**
 * Test configuration for data storage.
 * Uses a local data directory for testing.
 */
public class TestDataStorageConfig extends DataStorageConfig {
    
    public TestDataStorageConfig() {
        super();
        setBaseDirectory("data");
    }
    
    public TestDataStorageConfig(String baseDirectory) {
        super();
        setBaseDirectory(baseDirectory);
    }
}
