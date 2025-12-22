package com.openmc.upgrademanager.exception;

/**
 * Exception thrown when an upgrade operation fails
 */
public class UpgradeException extends Exception {
    
    public UpgradeException(String message) {
        super(message);
    }
    
    public UpgradeException(String message, Throwable cause) {
        super(message, cause);
    }
}
