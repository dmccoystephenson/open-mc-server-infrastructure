package com.openmc.upgrademanager.service;

import com.openmc.upgrademanager.exception.UpgradeException;
import com.openmc.upgrademanager.model.UpgradeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing Minecraft server upgrades
 */
@Service
@Slf4j
public class UpgradeService {

    @Value("${backup.directory:/backups}")
    private String backupDirectory;

    @Value("${backup.manager.url:http://backup-manager:8091/api/backups/trigger}")
    private String backupManagerUrl;

    @Value("${container.name:open-mc-server}")
    private String containerName;

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    @Value("${alerts.upgrade.start:true}")
    private boolean alertsUpgradeStart;

    @Value("${alerts.upgrade.complete:true}")
    private boolean alertsUpgradeComplete;

    @Value("${alerts.upgrade.failure:true}")
    private boolean alertsUpgradeFailure;

    private final RestTemplate restTemplate;
    private final MinecraftVersionService versionService;

    public UpgradeService(RestTemplate restTemplate, MinecraftVersionService versionService) {
        this.restTemplate = restTemplate;
        this.versionService = versionService;
    }

    /**
     * Perform a full upgrade of the Minecraft server
     */
    public UpgradeResult performUpgrade(String newVersion) throws UpgradeException {
        log.info("Starting upgrade process to version {}", newVersion);
        
        String currentVersion = versionService.getCurrentVersion();
        String backupPath = null;
        
        try {
            // Send start alert
            sendAlert("Server Upgrade Started", 
                     String.format("Starting upgrade from %s to %s", currentVersion, newVersion), 
                     "INFO", alertsUpgradeStart);
            
            // Step 1: Ensure backup exists
            log.info("Step 1/6: Checking for backup...");
            backupPath = ensureBackupExists();
            log.info("Backup verified: {}", backupPath);
            
            // Step 2: Stop the server
            log.info("Step 2/6: Stopping the server...");
            if (isServerRunning()) {
                stopServer();
                log.info("Server stopped successfully");
            } else {
                log.info("Server is not running, skipping stop step");
            }
            
            // Step 3: Update version in .env
            log.info("Step 3/6: Updating MINECRAFT_VERSION in .env...");
            versionService.updateEnvVersion(newVersion);
            
            // Step 4: Rebuild Docker image
            log.info("Step 4/6: Rebuilding Docker image with new version...");
            log.warn("This may take 10-15 minutes as it compiles Spigot from source...");
            rebuildDockerImage();
            log.info("Docker image rebuilt successfully");
            
            // Step 5: Start the server
            log.info("Step 5/6: Starting the server...");
            startServer();
            log.info("Server started");
            
            // Step 6: Monitor startup
            log.info("Step 6/6: Monitoring server startup...");
            monitorServerStartup();
            
            // Send completion alert
            sendAlert("Server Upgrade Complete", 
                     String.format("Successfully upgraded from %s to %s. Backup: %s", 
                                   currentVersion, newVersion, backupPath), 
                     "INFO", alertsUpgradeComplete);
            
            log.info("Upgrade completed successfully!");
            
            return new UpgradeResult(true, "Upgrade completed successfully", 
                                    currentVersion, newVersion, backupPath);
            
        } catch (Exception e) {
            log.error("Upgrade failed", e);
            
            // Send failure alert
            sendAlert("Server Upgrade Failed", 
                     String.format("Upgrade from %s to %s failed: %s. Backup: %s", 
                                   currentVersion, newVersion, e.getMessage(), backupPath), 
                     "ERROR", alertsUpgradeFailure);
            
            throw new UpgradeException("Upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensure a backup exists, create one if needed
     */
    private String ensureBackupExists() throws UpgradeException {
        try {
            // Check for recent backup (within last 60 minutes)
            Path backupDir = Paths.get(backupDirectory);
            if (!Files.exists(backupDir)) {
                log.warn("Backup directory does not exist: {}", backupDirectory);
                return triggerNewBackup();
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "backup-*")) {
                Path recentBackup = null;
                long currentTime = System.currentTimeMillis();
                
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        long lastModified = Files.getLastModifiedTime(entry).toMillis();
                        if (currentTime - lastModified < TimeUnit.MINUTES.toMillis(60)) {
                            Path tarFile = entry.resolve("mcserver-backup.tar.gz");
                            if (Files.exists(tarFile)) {
                                recentBackup = entry;
                                break;
                            }
                        }
                    }
                }
                
                if (recentBackup != null) {
                    log.info("Using recent backup: {}", recentBackup);
                    return recentBackup.toString();
                }
            }
            
            // No recent backup found, check for any existing backup
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "backup-*")) {
                Path latestBackup = null;
                long latestTime = 0;
                
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        Path tarFile = entry.resolve("mcserver-backup.tar.gz");
                        if (Files.exists(tarFile)) {
                            long lastModified = Files.getLastModifiedTime(entry).toMillis();
                            if (lastModified > latestTime) {
                                latestTime = lastModified;
                                latestBackup = entry;
                            }
                        }
                    }
                }
                
                if (latestBackup != null) {
                    log.info("Using existing backup: {}", latestBackup);
                    return latestBackup.toString();
                }
            }
            
            // No backup found, create a new one
            log.warn("No valid backup found, creating new backup...");
            return triggerNewBackup();
            
        } catch (IOException e) {
            throw new UpgradeException("Failed to check for backups", e);
        }
    }

    /**
     * Trigger a new backup via the backup-manager API
     */
    private String triggerNewBackup() throws UpgradeException {
        try {
            log.info("Triggering backup via backup-manager API: {}", backupManagerUrl);
            
            Map<String, Object> response = restTemplate.postForObject(
                backupManagerUrl, null, Map.class
            );
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                String backupPath = (String) response.get("backupPath");
                log.info("Backup created successfully: {}", backupPath);
                return backupPath;
            } else {
                String message = response != null ? (String) response.get("message") : "Unknown error";
                throw new UpgradeException("Backup creation failed: " + message);
            }
        } catch (Exception e) {
            throw new UpgradeException("Failed to trigger backup", e);
        }
    }

    /**
     * Check if the Minecraft server is running
     */
    private boolean isServerRunning() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("docker", "ps", "--format", "{{.Names}}");
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(containerName)) {
                    return true;
                }
            }
        }
        
        process.waitFor();
        return false;
    }

    /**
     * Stop the Minecraft server
     */
    private void stopServer() throws IOException, InterruptedException, UpgradeException {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", "cd / && ./down.sh");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("down.sh: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new UpgradeException("Failed to stop server, exit code: " + exitCode);
        }
    }

    /**
     * Rebuild the Docker image
     */
    private void rebuildDockerImage() throws IOException, InterruptedException, UpgradeException {
        ProcessBuilder pb = new ProcessBuilder("docker", "compose", "build", "--no-cache");
        pb.directory(new File("/"));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("docker build: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new UpgradeException("Docker build failed with exit code: " + exitCode);
        }
    }

    /**
     * Start the Minecraft server
     */
    private void startServer() throws IOException, InterruptedException, UpgradeException {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", "cd / && ./up.sh");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("up.sh: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new UpgradeException("Failed to start server, exit code: " + exitCode);
        }
    }

    /**
     * Monitor server startup
     */
    private void monitorServerStartup() throws InterruptedException {
        log.info("Waiting for server to initialize...");
        Thread.sleep(5000);
        
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", containerName, "--tail", "20");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            log.info("Recent server logs:");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  {}", line);
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            log.warn("Could not retrieve server logs", e);
        }
    }

    /**
     * Send an alert to the alert-manager
     */
    private void sendAlert(String title, String message, String level, boolean enabled) {
        if (!enabled) {
            log.debug("Alert skipped (disabled): {}", title);
            return;
        }
        
        try {
            Map<String, String> alert = new HashMap<>();
            alert.put("title", title);
            alert.put("message", message);
            alert.put("level", level);
            alert.put("source", "upgrade-manager");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(alert, headers);
            
            restTemplate.postForEntity(alertManagerUrl, request, String.class);
        } catch (Exception e) {
            log.error("Failed to send alert", e);
        }
    }
}
