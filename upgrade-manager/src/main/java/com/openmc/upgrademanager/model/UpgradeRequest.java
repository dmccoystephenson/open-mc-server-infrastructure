package com.openmc.upgrademanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for upgrade operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeRequest {
    
    /**
     * The new Minecraft version to upgrade to (e.g., "1.21.10")
     */
    private String newVersion;
}
