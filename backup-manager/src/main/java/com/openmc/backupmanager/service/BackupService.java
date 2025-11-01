package com.openmc.backupmanager.service;

import com.openmc.backupmanager.exception.BackupException;
import com.openmc.backupmanager.exception.BackupScriptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class BackupService {

    @Value("${backup.script.path:/backup.sh}")
    private String backupScriptPath;

    @Value("${backup.directory:/backups}")
    private String backupDirectory;

    @Value("${backup.max.size.mb:10240}")
    private long maxBackupSizeMb;

    /**
     * Run backup script once a day at 2 AM
     */
    @Scheduled(cron = "${backup.schedule:0 0 2 * * ?}")
    public void performScheduledBackup() {
        log.info("Starting scheduled backup at {}", java.time.LocalDateTime.now());
        log.info("Backup configuration: directory={}, maxSizeMb={}", backupDirectory, maxBackupSizeMb);
        try {
            runBackupScript();
            cleanupOldBackups();
            log.info("Scheduled backup completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled backup", e);
        }
    }

    /**
     * Execute the backup.sh script
     */
    public void runBackupScript() throws BackupException {
        File scriptFile = new File(backupScriptPath);
        if (!scriptFile.exists()) {
            log.error("Backup script not found at: {}", backupScriptPath);
            throw new BackupException("Backup script not found: " + backupScriptPath);
        }

        log.info("Executing backup script: {}", backupScriptPath);
        
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", backupScriptPath);
        processBuilder.directory(scriptFile.getParentFile());
        processBuilder.redirectErrorStream(true);
        
        try {
            Process process = processBuilder.start();
            
            // Log output from the script
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("backup.sh: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Backup script exited with code: {}", exitCode);
                throw new BackupScriptException("Backup script failed with exit code: " + exitCode, exitCode);
            }
            
            log.info("Backup script completed successfully");
        } catch (IOException | InterruptedException e) {
            throw new BackupException("Failed to execute backup script", e);
        }
    }

    /**
     * Clean up old backups to ensure the backups directory doesn't exceed the size limit
     */
    public void cleanupOldBackups() throws BackupException {
        Path backupDir = Paths.get(backupDirectory);
        
        if (!Files.exists(backupDir)) {
            log.warn("Backup directory does not exist: {}", backupDirectory);
            return;
        }

        long maxSizeBytes = maxBackupSizeMb * 1024 * 1024;
        long currentSize;
        try {
            currentSize = calculateDirectorySize(backupDir);
        } catch (IOException e) {
            throw new BackupException("Failed to calculate backup directory size", e);
        }
        
        log.info("Current backup directory size: {} MB (limit: {} MB)", 
                    currentSize / 1024 / 1024, maxBackupSizeMb);

        if (currentSize <= maxSizeBytes) {
            log.info("Backup directory size is within limits");
            return;
        }

        log.info("Backup directory exceeds size limit, cleaning up old backups");
        
        // Get all backup directories sorted by modification time (oldest first)
        List<Path> backupFolders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, 
                path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("backup-"))) {
            for (Path entry : stream) {
                backupFolders.add(entry);
            }
        } catch (IOException e) {
            throw new BackupException("Failed to list backup directories", e);
        }

        // Sort by last modified time (oldest first)
        backupFolders.sort(Comparator.comparingLong(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));

        // Delete oldest backups until we're under the size limit
        for (Path backupFolder : backupFolders) {
            if (currentSize <= maxSizeBytes) {
                break;
            }

            long folderSize;
            try {
                folderSize = calculateDirectorySize(backupFolder);
            } catch (IOException e) {
                log.warn("Failed to calculate size of backup folder: {}", backupFolder, e);
                continue;
            }
            
            log.info("Deleting old backup: {} (size: {} MB)", 
                        backupFolder.getFileName(), folderSize / 1024 / 1024);
            
            try {
                deleteDirectory(backupFolder);
                currentSize -= folderSize;
            } catch (IOException e) {
                log.error("Failed to delete backup folder: {}", backupFolder, e);
            }
        }

        log.info("Cleanup completed. New backup directory size: {} MB", 
                    currentSize / 1024 / 1024);
    }

    /**
     * Calculate the total size of a directory
     */
    private long calculateDirectorySize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }
        
        return Files.walk(directory)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        log.warn("Error getting size of file: {}", path, e);
                        return 0L;
                    }
                })
                .sum();
    }

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.error("Error deleting: {}", path, e);
                    }
                });
    }
}
