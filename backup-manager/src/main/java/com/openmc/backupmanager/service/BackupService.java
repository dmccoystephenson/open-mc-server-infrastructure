package com.openmc.backupmanager.service;

import com.openmc.backupmanager.exception.BackupException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class BackupService {

    private static final DateTimeFormatter BACKUP_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern ERROR_PATTERN = Pattern.compile(".*(error|warning|cannot|failed).*", Pattern.CASE_INSENSITIVE);
    private static final String BACKUP_FILE_NAME = "mcserver-backup.tar.gz";

    /**
     * Status of the most recent backup attempt.
     *
     * @param success     whether the backup completed successfully
     * @param timestamp   ISO-8601 timestamp of when the backup finished
     * @param backupPath  path to the backup directory (null on failure)
     * @param message     human-readable result message
     */
    public record LatestBackupStatus(boolean success, String timestamp, String backupPath, String message) {}

    @Value("${backup.directory:/backups}")
    private String backupDirectory;

    @Value("${backup.max.size.mb:10240}")
    private long maxBackupSizeMb;

    @Value("${source.directory:/mcserver}")
    private String sourceDirectory;

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    @Value("${alerts.backup.success:true}")
    private boolean alertsBackupSuccess;

    @Value("${alerts.backup.failure:true}")
    private boolean alertsBackupFailure;

    private final RestTemplate restTemplate;

    public BackupService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Run backup once a day at 2 AM
     */
    @Scheduled(cron = "${backup.schedule:0 0 2 * * ?}")
    public void performScheduledBackup() {
        log.info("Starting scheduled backup at {}", LocalDateTime.now());
        log.info("Backup configuration: directory={}, maxSizeMb={}", backupDirectory, maxBackupSizeMb);
        try {
            String backupPath = createBackup();
            log.info("Backup created at: {}", backupPath);
            cleanupOldBackups();
            log.info("Scheduled backup completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled backup", e);
        }
    }

    /**
     * Scan the backups directory and return the status of the most recent backup.
     * A backup is considered successful when its {@code mcserver-backup.tar.gz} file is present.
     * Returns {@code null} if no backup directories exist yet.
     *
     * @return latest backup status derived from disk, or null
     */
    public LatestBackupStatus getLatestBackupStatus() {
        Path backupDir = Paths.get(backupDirectory);
        if (!Files.exists(backupDir)) {
            return null;
        }

        Path latestDir = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir,
                path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("backup-"))) {
            for (Path entry : stream) {
                if (latestDir == null
                        || entry.getFileName().toString().compareTo(latestDir.getFileName().toString()) > 0) {
                    latestDir = entry;
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan backup directory {}: {}", backupDirectory, e.getMessage());
            return null;
        }

        if (latestDir == null) {
            return null;
        }

        Path backupFile = latestDir.resolve(BACKUP_FILE_NAME);
        boolean success = Files.exists(backupFile);

        String timestamp;
        try {
            Path timeSource = success ? backupFile : latestDir;
            timestamp = Files.getLastModifiedTime(timeSource).toInstant().toString();
        } catch (IOException e) {
            log.warn("Could not read modification time for {}: {}", latestDir, e.getMessage());
            timestamp = null;
        }

        if (success) {
            String msg = String.format("Backup available at %s", latestDir);
            return new LatestBackupStatus(true, timestamp, latestDir.toString(), msg);
        } else {
            String msg = String.format("Backup at %s is incomplete or failed", latestDir);
            return new LatestBackupStatus(false, timestamp, null, msg);
        }
    }

    /**
     * Create a backup of the Minecraft server volume using the filesystem.
     */
    public String createBackup() throws BackupException {
        return doCreateBackup();
    }

    private String doCreateBackup() throws BackupException {
        log.info("Creating backup...");
        log.info("Backup configuration: sourceDirectory={}, backupDirectory={}, maxSizeMb={}",
                sourceDirectory, backupDirectory, maxBackupSizeMb);

        // Create backup directory with timestamp
        String backupSubdir = "backup-" + LocalDateTime.now().format(BACKUP_DIR_FORMAT);
        Path backupDir = Paths.get(backupDirectory, backupSubdir);

        try {
            Files.createDirectories(backupDir);
            log.info("Created backup directory: {}", backupDir);
        } catch (IOException e) {
            sendAlert("Backup Failed", "Failed to create backup directory: " + e.getMessage(), "ERROR", alertsBackupFailure);
            throw new BackupException("Failed to create backup directory", e);
        }

        // Check if source directory is available
        if (!checkSourceDirectoryAvailable()) {
            String errorMsg = String.format(
                    "Source directory '%s' does not exist or is empty. Please ensure the server has been started at least once.",
                    sourceDirectory);
            log.error(errorMsg);
            sendAlert("Backup Failed", errorMsg, "ERROR", alertsBackupFailure);
            throw new BackupException(errorMsg);
        }
        log.info("Source directory '{}' found", sourceDirectory);

        // Create backup using tar directly on the mounted filesystem
        log.info("Creating compressed backup archive (this may take a while)...");

        Path backupFile = backupDir.resolve(BACKUP_FILE_NAME);
        List<String> tarCommand = new ArrayList<>();
        tarCommand.add("tar");
        tarCommand.add("czf");
        tarCommand.add(backupFile.toString());
        tarCommand.add("-C");
        tarCommand.add(sourceDirectory);
        tarCommand.add(".");

        ProcessBuilder pb = new ProcessBuilder(tarCommand);
        pb.redirectErrorStream(true);

        int exitCode;
        try {
            Process process = pb.start();

            // Capture output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (ERROR_PATTERN.matcher(line).matches()) {
                        log.warn("tar: {}", line);
                    }
                }
            }

            exitCode = process.waitFor();
        } catch (IOException | InterruptedException e) {
            String errorMsg = "Failed to execute backup command: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlert("Backup Failed", errorMsg, "ERROR", alertsBackupFailure);
            throw new BackupException(errorMsg, e);
        }

        // Exit code 1 from tar means "some files changed during backup" which is acceptable
        if (exitCode == 0) {
            log.info("Backup archive created successfully");
        } else if (exitCode == 1) {
            log.warn("Backup completed with warnings (files changed during backup)");
            log.info("This is normal for a running server and the backup should still be usable");
        } else {
            String errorMsg = String.format("Backup failed with exit code: %d", exitCode);
            log.error(errorMsg);
            sendAlert("Backup Failed", errorMsg, "ERROR", alertsBackupFailure);
            throw new BackupException(errorMsg);
        }

        // Verify backup was created
        if (!Files.exists(backupFile)) {
            String errorMsg = "Backup verification failed! File not created.";
            log.error(errorMsg);
            sendAlert("Backup Failed", errorMsg, "ERROR", alertsBackupFailure);
            throw new BackupException(errorMsg);
        }

        long backupSize;
        try {
            backupSize = Files.size(backupFile);
        } catch (IOException e) {
            backupSize = 0;
        }

        String backupSizeStr = formatFileSize(backupSize);
        log.info("Backup created successfully: {} ({})", backupFile, backupSizeStr);

        String successMsg = String.format("Minecraft server backup created successfully. Size: %s, Location: %s",
                                         backupSizeStr, backupDir);
        sendAlert("Backup Completed", successMsg, "INFO", alertsBackupSuccess);
        return backupDir.toString();
    }

    /**
     * Check if the source directory is available and contains data.
     */
    private boolean checkSourceDirectoryAvailable() {
        Path src = Paths.get(sourceDirectory);
        if (!Files.isDirectory(src)) {
            log.warn("Source directory '{}' does not exist or is not a directory", sourceDirectory);
            return false;
        }
        try (Stream<Path> entries = Files.list(src)) {
            return entries.findAny().isPresent();
        } catch (IOException e) {
            log.error("Failed to read source directory '{}': {}", sourceDirectory, e.getMessage());
            return false;
        }
    }

    /**
     * Send an alert to the alert manager
     */
    private void sendAlert(String title, String message, String level, boolean enabled) {
        if (!enabled) {
            log.debug("Alert skipped (disabled): {}", title);
            return;
        }

        try {
            Map<String, String> alertData = new HashMap<>();
            alertData.put("title", title);
            alertData.put("message", message);
            alertData.put("level", level);
            alertData.put("source", "backup-manager");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(alertData, headers);

            log.info("Sending alert to {}: {} ({})", alertManagerUrl, title, level);
            restTemplate.postForEntity(alertManagerUrl, request, String.class);
            log.info("Alert sent successfully");
        } catch (Exception e) {
            log.warn("Failed to send alert: {}", e.getMessage());
        }
    }

    /**
     * Format file size in human-readable format
     */
    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + "B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1fK", sizeBytes / 1024.0);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1fM", sizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1fG", sizeBytes / (1024.0 * 1024.0 * 1024.0));
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
