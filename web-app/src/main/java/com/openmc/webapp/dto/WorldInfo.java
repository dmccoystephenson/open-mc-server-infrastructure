package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class WorldInfo {
    @Schema(description = "World directory name")
    private String name;
    @Schema(description = "Approximate size in megabytes")
    private long sizeMb;
    @Schema(description = "ISO-8601 last-modified timestamp of the directory")
    private String lastModified;
    @Schema(description = "Whether this is the currently active world")
    private boolean active;

    public WorldInfo() {}

    public WorldInfo(String name, long sizeMb, String lastModified, boolean active) {
        this.name = name;
        this.sizeMb = sizeMb;
        this.lastModified = lastModified;
        this.active = active;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSizeMb() { return sizeMb; }
    public void setSizeMb(long sizeMb) { this.sizeMb = sizeMb; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
