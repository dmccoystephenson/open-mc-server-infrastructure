package com.openmc.backupmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "backup.script.path=/tmp/test-backup.sh",
    "backup.directory=/tmp/test-backups",
    "backup.max.size.mb=1"
})
@DisplayName("BackupService Tests")
class BackupServiceTest {

    @Autowired
    private BackupService backupService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Set the backup directory to our temp directory
        ReflectionTestUtils.setField(backupService, "backupDirectory", tempDir.toString());
    }

    @Test
    @DisplayName("Should calculate directory size correctly")
    void shouldCalculateDirectorySize() throws IOException {
        // Create test files
        Path testFile1 = tempDir.resolve("file1.txt");
        Path testFile2 = tempDir.resolve("file2.txt");
        Files.writeString(testFile1, "test content 1");
        Files.writeString(testFile2, "test content 2");

        // Use reflection to access private method
        long size = (long) ReflectionTestUtils.invokeMethod(backupService, "calculateDirectorySize", tempDir);
        
        assertTrue(size > 0, "Directory size should be greater than 0");
    }

    @Test
    @DisplayName("Should handle non-existent backup directory gracefully")
    void shouldHandleNonExistentDirectory() throws IOException {
        Path nonExistentDir = tempDir.resolve("non-existent");
        ReflectionTestUtils.setField(backupService, "backupDirectory", nonExistentDir.toString());

        // Should not throw an exception
        assertDoesNotThrow(() -> backupService.cleanupOldBackups());
    }

    @Test
    @DisplayName("Should delete directory recursively")
    void shouldDeleteDirectoryRecursively() throws IOException {
        // Create a directory with subdirectories and files
        Path testDir = tempDir.resolve("backup-test");
        Path subDir = testDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(testDir.resolve("file1.txt"), "content");
        Files.writeString(subDir.resolve("file2.txt"), "content");

        // Delete the directory
        ReflectionTestUtils.invokeMethod(backupService, "deleteDirectory", testDir);

        assertFalse(Files.exists(testDir), "Directory should be deleted");
    }

    @Test
    @DisplayName("Should cleanup old backups when exceeding size limit")
    void shouldCleanupOldBackups() throws IOException, InterruptedException {
        // Set a very small size limit
        ReflectionTestUtils.setField(backupService, "maxBackupSizeMb", 0L);

        // Create backup directories
        Path backup1 = tempDir.resolve("backup-20240101-120000");
        Path backup2 = tempDir.resolve("backup-20240102-120000");
        Files.createDirectories(backup1);
        Files.createDirectories(backup2);
        
        // Create files in each backup
        Files.writeString(backup1.resolve("data.txt"), "backup 1 data with some content");
        Thread.sleep(100); // Ensure different timestamps
        Files.writeString(backup2.resolve("data.txt"), "backup 2 data with some content");

        // Run cleanup
        backupService.cleanupOldBackups();

        // Should have deleted at least one backup
        boolean backup1Exists = Files.exists(backup1);
        boolean backup2Exists = Files.exists(backup2);
        
        // At least one should be deleted due to size limit
        assertFalse(backup1Exists && backup2Exists, 
            "At least one backup should be deleted when exceeding size limit");
    }
}
