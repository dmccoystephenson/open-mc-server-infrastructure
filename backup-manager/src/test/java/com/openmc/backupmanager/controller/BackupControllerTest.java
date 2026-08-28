package com.openmc.backupmanager.controller;

import com.openmc.backupmanager.dto.LatestBackupResponse;
import com.openmc.backupmanager.exception.BackupException;
import com.openmc.backupmanager.mapper.BackupMapper;
import com.openmc.backupmanager.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BackupController.class)
@DisplayName("BackupController Tests")
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    @MockBean
    private BackupMapper backupMapper;

    @Test
    @DisplayName("Should trigger backup successfully")
    void shouldTriggerBackupSuccessfully() throws Exception {
        String expectedBackupPath = "/backups/backup-20241211-120000";
        when(backupService.createBackupAndCleanup()).thenReturn(expectedBackupPath);

        mockMvc.perform(post("/api/backups/trigger")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Backup completed successfully"))
                .andExpect(jsonPath("$.backupPath").value(expectedBackupPath));

        verify(backupService, times(1)).createBackupAndCleanup();
    }

    @Test
    @DisplayName("Should return error when backup fails")
    void shouldReturnErrorWhenBackupFails() throws Exception {
        String errorMessage = "Backup volume not found";
        when(backupService.createBackupAndCleanup()).thenThrow(new BackupException(errorMessage));

        mockMvc.perform(post("/api/backups/trigger")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(errorMessage));

        verify(backupService, times(1)).createBackupAndCleanup();
        // Retention is guaranteed inside createBackupAndCleanup rather than sequenced here, so the
        // controller can no longer skip the prune when a backup fails. Asserting never() on
        // cleanupOldBackups would re-encode exactly that bug.
        verify(backupService, never()).createBackup();
    }

    @Test
    @DisplayName("Should return no-backup status when no backup has run")
    void shouldReturnNoneWhenNoBackupHasRun() throws Exception {
        when(backupService.getLatestBackupStatus()).thenReturn(null);

        mockMvc.perform(get("/api/backups/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("No backup has been performed yet"));
    }

    @Test
    @DisplayName("Should return latest successful backup status")
    void shouldReturnLatestSuccessfulBackupStatus() throws Exception {
        BackupService.LatestBackupStatus status = new BackupService.LatestBackupStatus(
                true, "2024-01-01T02:00:00", "/backups/backup-20240101-020000",
                "Minecraft server backup created successfully.");

        LatestBackupResponse response = LatestBackupResponse.builder()
                .available(true)
                .success(true)
                .timestamp("2024-01-01T02:00:00")
                .backupPath("/backups/backup-20240101-020000")
                .message("Minecraft server backup created successfully.")
                .build();

        when(backupService.getLatestBackupStatus()).thenReturn(status);
        when(backupMapper.toLatestBackupResponse(status)).thenReturn(response);

        mockMvc.perform(get("/api/backups/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").value("2024-01-01T02:00:00"))
                .andExpect(jsonPath("$.backupPath").value("/backups/backup-20240101-020000"));
    }

    @Test
    @DisplayName("Should return latest failed backup status without backupPath")
    void shouldReturnLatestFailedBackupStatus() throws Exception {
        BackupService.LatestBackupStatus status = new BackupService.LatestBackupStatus(
                false, "2024-01-01T02:05:00", null, "Backup failed: volume not found");

        LatestBackupResponse response = LatestBackupResponse.builder()
                .available(true)
                .success(false)
                .timestamp("2024-01-01T02:05:00")
                .message("Backup failed: volume not found")
                .build();

        when(backupService.getLatestBackupStatus()).thenReturn(status);
        when(backupMapper.toLatestBackupResponse(status)).thenReturn(response);

        mockMvc.perform(get("/api/backups/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Backup failed: volume not found"))
                .andExpect(jsonPath("$.backupPath").doesNotExist());
    }
}
