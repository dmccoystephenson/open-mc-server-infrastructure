package com.openmc.upgrademanager.service;

import com.openmc.upgrademanager.exception.UpgradeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
    "env.file.path=/tmp/test.env",
    "alerts.upgrade.start=false",
    "alerts.upgrade.complete=false",
    "alerts.upgrade.failure=false"
})
@DisplayName("UpgradeService Tests")
class UpgradeServiceTest {

    @Autowired
    private UpgradeService upgradeService;

    @MockBean
    private RestTemplate restTemplate;

    @TempDir
    Path tempDir;

    private Path testEnvFile;

    @BeforeEach
    void setUp() throws IOException {
        testEnvFile = tempDir.resolve("test.env");
        ReflectionTestUtils.setField(upgradeService, "envFilePath", testEnvFile.toString());
    }

    @Test
    @DisplayName("Should get current version from .env file")
    void shouldGetCurrentVersion() throws IOException, UpgradeException {
        // Create test .env file
        Files.writeString(testEnvFile, 
            "MINECRAFT_VERSION=1.20.0\n" +
            "OTHER_VAR=value\n"
        );

        String version = upgradeService.getCurrentVersion();
        assertEquals("1.20.0", version);
    }

    @Test
    @DisplayName("Should return 'unknown' when .env file doesn't exist")
    void shouldReturnUnknownWhenEnvFileDoesNotExist() throws UpgradeException {
        String version = upgradeService.getCurrentVersion();
        assertEquals("unknown", version);
    }

    @Test
    @DisplayName("Should return 'unknown' when version not found in .env")
    void shouldReturnUnknownWhenVersionNotInEnv() throws IOException, UpgradeException {
        Files.writeString(testEnvFile, "OTHER_VAR=value\n");

        String version = upgradeService.getCurrentVersion();
        assertEquals("unknown", version);
    }

    @Test
    @DisplayName("Should update version in .env file")
    void shouldUpdateVersionInEnv() throws IOException, UpgradeException {
        // Create initial .env file
        Files.writeString(testEnvFile,
            "MINECRAFT_VERSION=1.20.0\n" +
            "OTHER_VAR=value\n"
        );

        upgradeService.updateEnvVersion("1.21.0");

        String content = Files.readString(testEnvFile);
        assertTrue(content.contains("MINECRAFT_VERSION=1.21.0"));
        assertTrue(content.contains("OTHER_VAR=value"));
        assertFalse(content.contains("MINECRAFT_VERSION=1.20.0"));
    }

    @Test
    @DisplayName("Should add version to .env if not present")
    void shouldAddVersionIfNotPresent() throws IOException, UpgradeException {
        // Create .env file without version
        Files.writeString(testEnvFile, "OTHER_VAR=value\n");

        upgradeService.updateEnvVersion("1.21.0");

        String content = Files.readString(testEnvFile);
        assertTrue(content.contains("MINECRAFT_VERSION=1.21.0"));
        assertTrue(content.contains("OTHER_VAR=value"));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent .env file")
    void shouldThrowExceptionWhenUpdatingNonExistentEnv() {
        assertThrows(UpgradeException.class, () -> {
            upgradeService.updateEnvVersion("1.21.0");
        });
    }

    @Test
    @DisplayName("Should check if server is running")
    void shouldCheckIfServerIsRunning() {
        // This test is environment-dependent, just verify it doesn't throw
        assertDoesNotThrow(() -> upgradeService.isServerRunning());
    }

    @Test
    @DisplayName("Should send alert when enabled")
    void shouldSendAlertWhenEnabled() {
        // Set up mock response
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("success"));

        // Should not throw an exception
        assertDoesNotThrow(() -> 
            upgradeService.sendAlert("Test", "Message", "INFO", true)
        );
    }

    @Test
    @DisplayName("Should skip alert when disabled")
    void shouldSkipAlertWhenDisabled() {
        // Should not throw an exception and should not call restTemplate
        assertDoesNotThrow(() -> 
            upgradeService.sendAlert("Test", "Message", "INFO", false)
        );
    }

    @Test
    @DisplayName("Should handle alert sending failure gracefully")
    void shouldHandleAlertSendingFailureGracefully() {
        // Set up mock to throw exception
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        // Should not throw an exception (should log warning instead)
        assertDoesNotThrow(() -> 
            upgradeService.sendAlert("Test", "Message", "INFO", true)
        );
    }

    @Test
    @DisplayName("Should trigger backup successfully")
    void shouldTriggerBackupSuccessfully() throws UpgradeException {
        // Set up mock response for successful backup
        Map<String, Object> backupResponse = new HashMap<>();
        backupResponse.put("success", true);
        backupResponse.put("backupPath", "/backups/backup-20240101-120000");
        
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(backupResponse));

        String backupPath = upgradeService.triggerBackup();
        assertEquals("/backups/backup-20240101-120000", backupPath);
    }

    @Test
    @DisplayName("Should throw exception when backup fails")
    void shouldThrowExceptionWhenBackupFails() {
        // Set up mock response for failed backup
        Map<String, Object> backupResponse = new HashMap<>();
        backupResponse.put("success", false);
        backupResponse.put("message", "Backup failed");
        
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(backupResponse));

        assertThrows(UpgradeException.class, () -> {
            upgradeService.triggerBackup();
        });
    }

    @Test
    @DisplayName("Should throw exception when backup request fails")
    void shouldThrowExceptionWhenBackupRequestFails() {
        // Set up mock to return error status
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        assertThrows(UpgradeException.class, () -> {
            upgradeService.triggerBackup();
        });
    }

    @Test
    @DisplayName("Should monitor startup without errors")
    void shouldMonitorStartupWithoutErrors() {
        // This is mostly a logging method, just verify it doesn't throw
        assertDoesNotThrow(() -> upgradeService.monitorStartup());
    }
}
