package com.openmc.alertmanager.config;

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
     * Get the full path for a data file relative to the base directory.
     *
     * @param filename the filename relative to the base directory
     * @return the full path
     */
    public String getFilePath(String filename) {
        return java.nio.file.Paths.get(baseDirectory, filename).toString();
    }
}
