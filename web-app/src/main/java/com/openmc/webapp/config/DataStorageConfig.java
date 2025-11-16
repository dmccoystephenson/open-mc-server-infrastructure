package com.openmc.webapp.config;

/**
 * Configuration for data storage paths.
 * Allows configurable data directory for Docker volume mounting.
 */
public class DataStorageConfig {
    
    /**
     * Base directory for data storage. Should be mounted as a Docker volume.
     * Default: /app/data
     */
    private String baseDirectory = "/app/data";
    
    public String getBaseDirectory() {
        return baseDirectory;
    }
    
    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }
    
    /**
     * Get the full path for a data file relative to the base directory
     * @param filename The filename relative to the base directory
     * @return The full path
     */
    public String getFilePath(String filename) {
        String base = baseDirectory.endsWith("/") ? baseDirectory : baseDirectory + "/";
        return base + filename;
    }
}
