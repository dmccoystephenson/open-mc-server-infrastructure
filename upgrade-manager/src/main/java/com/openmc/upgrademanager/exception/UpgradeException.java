package com.openmc.upgrademanager.exception;

/**
 * Exception thrown when upgrade operations fail
 */
public class UpgradeException extends Exception {
    
    public UpgradeException(String message) {
        super(message);
    }
    
    public UpgradeException(String message, Throwable cause) {
        super(message, cause);
    }
}
