package com.openmc.upgrademanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the result of an upgrade operation
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpgradeResult {
    private boolean success;
    private String message;
    private String previousVersion;
    private String newVersion;
    private String backupPath;
}
