package com.openmc.upgrademanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Minecraft version information
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MinecraftVersion {
    private String version;
    private String releaseTime;
    private String type; // release, snapshot, etc.
}
