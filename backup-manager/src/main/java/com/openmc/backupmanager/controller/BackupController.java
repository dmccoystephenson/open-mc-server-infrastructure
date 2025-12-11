package com.openmc.backupmanager.controller;

import com.openmc.backupmanager.exception.BackupException;
import com.openmc.backupmanager.service.BackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
