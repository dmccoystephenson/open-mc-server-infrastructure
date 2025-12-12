package com.openmc.backupmanager.controller;

import com.openmc.backupmanager.exception.BackupException;
import com.openmc.backupmanager.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BackupController.class)
@DisplayName("BackupController Tests")
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    @Test
    @DisplayName("Should trigger backup successfully")
    void shouldTriggerBackupSuccessfully() throws Exception {
        // Given
        String expectedBackupPath = "/backups/backup-20241211-120000";
        when(backupService.createBackup()).thenReturn(expectedBackupPath);
        doNothing().when(backupService).cleanupOldBackups();

        // When & Then
        mockMvc.perform(post("/api/backups/trigger")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Backup completed successfully"))
                .andExpect(jsonPath("$.backupPath").value(expectedBackupPath));

        verify(backupService, times(1)).createBackup();
        verify(backupService, times(1)).cleanupOldBackups();
    }

    @Test
    @DisplayName("Should return error when backup fails")
    void shouldReturnErrorWhenBackupFails() throws Exception {
        // Given
        String errorMessage = "Backup volume not found";
        when(backupService.createBackup()).thenThrow(new BackupException(errorMessage));

        // When & Then
        mockMvc.perform(post("/api/backups/trigger")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Backup failed: " + errorMessage));

        verify(backupService, times(1)).createBackup();
        verify(backupService, never()).cleanupOldBackups();
    }
}
