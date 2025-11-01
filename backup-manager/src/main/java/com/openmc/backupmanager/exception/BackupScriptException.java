package com.openmc.backupmanager.exception;

/**
 * Exception thrown when backup script execution fails
 */
public class BackupScriptException extends BackupException {
    
    private final int exitCode;
    
    public BackupScriptException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }
    
    public int getExitCode() {
        return exitCode;
    }
}
