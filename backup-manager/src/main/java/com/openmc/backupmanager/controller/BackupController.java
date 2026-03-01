package com.openmc.backupmanager.controller;

import com.openmc.backupmanager.exception.BackupException;
import com.openmc.backupmanager.service.BackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for backup operations
 */
@RestController
@RequestMapping("/api/backups")
@Slf4j
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * Trigger a manual backup
     * POST /api/backups/trigger
     * 
     * @return Response with backup status and location
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerBackup() {
        log.info("Manual backup triggered via API");
        Map<String, Object> response = new HashMap<>();
        
        try {
            String backupPath = backupService.createBackup();
            backupService.cleanupOldBackups();
            
            response.put("success", true);
            response.put("message", "Backup completed successfully");
            response.put("backupPath", backupPath);
            
            log.info("Manual backup completed successfully: {}", backupPath);
            return ResponseEntity.ok(response);
            
        } catch (BackupException e) {
            log.error("Manual backup failed", e);
            response.put("success", false);
            response.put("message", "Backup failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get the status of the most recent backup attempt.
     * GET /api/backups/latest
     *
     * @return Response with latest backup status, or available=false if no backup has run
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestBackup() {
        log.info("Fetching latest backup status");
        BackupService.LatestBackupStatus status = backupService.getLatestBackupStatus();

        Map<String, Object> response = new HashMap<>();
        if (status == null) {
            response.put("available", false);
            response.put("message", "No backup has been performed yet");
            return ResponseEntity.ok(response);
        }

        response.put("available", true);
        response.put("success", status.success());
        response.put("timestamp", status.timestamp());
        response.put("message", status.message());
        if (status.backupPath() != null) {
            response.put("backupPath", status.backupPath());
        }
        return ResponseEntity.ok(response);
    }
}
