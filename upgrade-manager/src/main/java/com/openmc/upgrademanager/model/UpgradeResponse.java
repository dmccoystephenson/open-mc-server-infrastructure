package com.openmc.upgrademanager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for upgrade operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeResponse {
    
    /**
     * Whether the upgrade was successful
     */
    private boolean success;
    
    /**
     * Human-readable message about the upgrade status
     */
    private String message;
    
    /**
     * Previous Minecraft version
     */
    private String previousVersion;
    
    /**
     * New Minecraft version
     */
    private String newVersion;
    
    /**
     * Path to the backup created during upgrade
     */
    private String backupPath;
    
    /**
     * Error message if the upgrade failed
     */
    private String error;
}
