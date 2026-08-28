package com.openmc.backupmanager.controller;

import com.openmc.backupmanager.dto.BackupTriggerResponse;
import com.openmc.backupmanager.dto.LatestBackupResponse;
import com.openmc.backupmanager.exception.BackupException;
import com.openmc.backupmanager.mapper.BackupMapper;
import com.openmc.backupmanager.service.BackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backups")
@Validated
@Slf4j
public class BackupController {

    private final BackupService backupService;
    private final BackupMapper backupMapper;

    public BackupController(BackupService backupService, BackupMapper backupMapper) {
        this.backupService = backupService;
        this.backupMapper = backupMapper;
    }

    @PostMapping("/trigger")
    public ResponseEntity<BackupTriggerResponse> triggerBackup() throws BackupException {
        log.info("Manual backup triggered via API");

        String backupPath = backupService.createBackupAndCleanup();

        BackupTriggerResponse response = BackupTriggerResponse.builder()
                .success(true)
                .message("Backup completed successfully")
                .backupPath(backupPath)
                .build();

        log.info("Manual backup completed successfully: {}", backupPath);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    public ResponseEntity<LatestBackupResponse> getLatestBackup() {
        log.info("Fetching latest backup status");
        BackupService.LatestBackupStatus status = backupService.getLatestBackupStatus();

        if (status == null) {
            LatestBackupResponse response = LatestBackupResponse.builder()
                    .available(false)
                    .message("No backup has been performed yet")
                    .build();
            return ResponseEntity.ok(response);
        }

        LatestBackupResponse response = backupMapper.toLatestBackupResponse(status);
        return ResponseEntity.ok(response);
    }
}

