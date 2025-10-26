package com.openmc.backupmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

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
        logger.info("Starting scheduled backup at {}", java.time.LocalDateTime.now());
        try {
            runBackupScript();
            cleanupOldBackups();
            logger.info("Scheduled backup completed successfully");
        } catch (Exception e) {
            logger.error("Error during scheduled backup", e);
        }
    }

    /**
     * Execute the backup.sh script
     */
    public void runBackupScript() throws IOException, InterruptedException {
        File scriptFile = new File(backupScriptPath);
        if (!scriptFile.exists()) {
            logger.error("Backup script not found at: {}", backupScriptPath);
            throw new IOException("Backup script not found: " + backupScriptPath);
        }

        logger.info("Executing backup script: {}", backupScriptPath);
        
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", backupScriptPath);
        processBuilder.directory(scriptFile.getParentFile());
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // Log output from the script
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("backup.sh: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.error("Backup script exited with code: {}", exitCode);
            throw new IOException("Backup script failed with exit code: " + exitCode);
        }
        
        logger.info("Backup script completed successfully");
    }

    /**
     * Clean up old backups to ensure the backups directory doesn't exceed the size limit
     */
    public void cleanupOldBackups() throws IOException {
        Path backupDir = Paths.get(backupDirectory);
        
        if (!Files.exists(backupDir)) {
            logger.warn("Backup directory does not exist: {}", backupDirectory);
            return;
        }

        long maxSizeBytes = maxBackupSizeMb * 1024 * 1024;
        long currentSize = calculateDirectorySize(backupDir);
        
        logger.info("Current backup directory size: {} MB (limit: {} MB)", 
                    currentSize / 1024 / 1024, maxBackupSizeMb);

        if (currentSize <= maxSizeBytes) {
            logger.info("Backup directory size is within limits");
            return;
        }

        logger.info("Backup directory exceeds size limit, cleaning up old backups");
        
        // Get all backup directories sorted by modification time (oldest first)
        List<Path> backupFolders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, 
                path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("backup-"))) {
            for (Path entry : stream) {
                backupFolders.add(entry);
            }
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

            long folderSize = calculateDirectorySize(backupFolder);
            logger.info("Deleting old backup: {} (size: {} MB)", 
                        backupFolder.getFileName(), folderSize / 1024 / 1024);
            
            deleteDirectory(backupFolder);
            currentSize -= folderSize;
        }

        logger.info("Cleanup completed. New backup directory size: {} MB", 
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
                        logger.warn("Error getting size of file: {}", path, e);
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
                        logger.error("Error deleting: {}", path, e);
                    }
                });
    }
}
