package com.openmc.backupmanager.mapper;

import com.openmc.backupmanager.dto.LatestBackupResponse;
import com.openmc.backupmanager.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BackupMapper Tests")
class BackupMapperTest {

    private BackupMapper backupMapper;

    @BeforeEach
    void setUp() {
        backupMapper = Mappers.getMapper(BackupMapper.class);
    }

    @Test
    @DisplayName("Should map a successful backup status to a response")
    void shouldMapSuccessfulBackupStatusToResponse() {
        BackupService.LatestBackupStatus status = new BackupService.LatestBackupStatus(
                true, "2024-01-01T02:00:00Z", "/backups/backup-20240101-020000",
                "Minecraft server backup created successfully.");

        LatestBackupResponse response = backupMapper.toLatestBackupResponse(status);

        assertTrue(response.isAvailable());
        assertTrue(response.getSuccess());
        assertEquals("2024-01-01T02:00:00Z", response.getTimestamp());
        assertEquals("/backups/backup-20240101-020000", response.getBackupPath());
        assertEquals("Minecraft server backup created successfully.", response.getMessage());
    }

    @Test
    @DisplayName("Should map a failed backup status to a response without a backup path")
    void shouldMapFailedBackupStatusToResponse() {
        BackupService.LatestBackupStatus status = new BackupService.LatestBackupStatus(
                false, "2024-01-01T02:05:00Z", null, "Backup failed: volume not found");

        LatestBackupResponse response = backupMapper.toLatestBackupResponse(status);

        assertTrue(response.isAvailable());
        assertFalse(response.getSuccess());
        assertEquals("2024-01-01T02:05:00Z", response.getTimestamp());
        assertNull(response.getBackupPath());
        assertEquals("Backup failed: volume not found", response.getMessage());
    }

    @Test
    @DisplayName("Should always set available to true regardless of status content")
    void shouldAlwaysSetAvailableToTrue() {
        BackupService.LatestBackupStatus status = new BackupService.LatestBackupStatus(
                false, null, null, null);

        LatestBackupResponse response = backupMapper.toLatestBackupResponse(status);

        assertTrue(response.isAvailable());
    }

    @Test
    @DisplayName("Should return null when the status is null")
    void shouldReturnNullWhenStatusIsNull() {
        LatestBackupResponse response = backupMapper.toLatestBackupResponse(null);

        assertNull(response);
    }
}
