package com.openmc.backupmanager.service;

import com.openmc.backupmanager.exception.BackupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "backup.directory=/tmp/test-backups",
    "backup.max.size.mb=1",
    "source.directory=/tmp/test-mcserver",
    "alerts.backup.success=false",
    "alerts.backup.failure=false"
})
@DisplayName("BackupService Tests")
class BackupServiceTest {

    @Autowired
    private BackupService backupService;

    @MockBean
    private RestTemplate restTemplate;

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
    void shouldHandleNonExistentDirectory() {
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
    void shouldCleanupOldBackups() throws BackupException, IOException, InterruptedException {
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

    @Test
    @DisplayName("Keeps the newest backup even when it alone exceeds the size limit")
    void shouldKeepNewestBackupWhenItAloneExceedsLimit() throws BackupException, IOException, InterruptedException {
        // Mirrors a world larger than the cap: every backup taken is over the limit on its own.
        ReflectionTestUtils.setField(backupService, "maxBackupSizeMb", 0L);

        Path older = tempDir.resolve("backup-20240101-120000");
        Path newest = tempDir.resolve("backup-20240102-120000");
        Files.createDirectories(older);
        Files.writeString(older.resolve("mcserver-backup.tar.gz"), "older backup payload");
        Thread.sleep(100); // Ensure different timestamps
        Files.createDirectories(newest);
        Files.writeString(newest.resolve("mcserver-backup.tar.gz"), "newest backup payload");

        backupService.cleanupOldBackups();

        assertTrue(Files.exists(newest),
                "The newest backup must survive cleanup, or a world over the cap is left with no backup at all");
        assertFalse(Files.exists(older), "Older backups should still be reclaimed");
    }

    @Test
    @DisplayName("Keeps a lone oversized backup rather than emptying the directory")
    void shouldKeepSoleBackupWhenOverLimit() throws BackupException, IOException {
        ReflectionTestUtils.setField(backupService, "maxBackupSizeMb", 0L);

        Path only = tempDir.resolve("backup-20240103-120000");
        Files.createDirectories(only);
        Files.writeString(only.resolve("mcserver-backup.tar.gz"), "the only backup there is");

        backupService.cleanupOldBackups();

        assertTrue(Files.exists(only), "The only backup on disk must not be deleted to satisfy the size cap");
    }

    @Test
    @DisplayName("Tolerates an over-limit directory holding no backup-* folders")
    void shouldTolerateNoBackupFolders() throws IOException {
        ReflectionTestUtils.setField(backupService, "maxBackupSizeMb", 0L);
        Files.writeString(tempDir.resolve("stray-file.txt"), "not a backup directory");

        assertDoesNotThrow(() -> backupService.cleanupOldBackups());
    }

    @Test
    @DisplayName("Prunes old backups even when the backup itself fails")
    void shouldCleanupWhenBackupFails() throws IOException, InterruptedException {
        // The regression this guards: retention used to run only after a successful backup, so a
        // single unreadable file under the source directory (tar exits 2) stopped the prune for as
        // long as the failure went unnoticed, and the backup directory grew past its cap unchecked.
        ReflectionTestUtils.setField(backupService, "maxBackupSizeMb", 0L);
        ReflectionTestUtils.setField(backupService, "sourceDirectory",
                tempDir.resolve("no-mcserver").toString());

        Path older = tempDir.resolve("backup-20240101-120000");
        Files.createDirectories(older);
        Files.writeString(older.resolve("mcserver-backup.tar.gz"), "older backup payload");
        Thread.sleep(100); // Ensure different timestamps
        Path newer = tempDir.resolve("backup-20240102-120000");
        Files.createDirectories(newer);
        Files.writeString(newer.resolve("mcserver-backup.tar.gz"), "newer backup payload");

        assertThrows(BackupException.class, () -> backupService.createBackupAndCleanup(),
                "The backup failure must still surface to the caller");

        assertFalse(Files.exists(older),
                "Retention must run even when the backup fails, or the directory grows past its cap");
    }

    @Test
    @DisplayName("Prunes old backups after a successful backup")
    void shouldCleanupAfterSuccessfulBackup() throws IOException, InterruptedException, BackupException {
        ReflectionTestUtils.setField(backupService, "maxBackupSizeMb", 0L);
        Path source = tempDir.resolve("mcserver");
        Files.createDirectories(source);
        Files.writeString(source.resolve("server.properties"), "level-name=world");
        ReflectionTestUtils.setField(backupService, "sourceDirectory", source.toString());

        Path older = tempDir.resolve("backup-20240101-120000");
        Files.createDirectories(older);
        Files.writeString(older.resolve("mcserver-backup.tar.gz"), "older backup payload");
        Thread.sleep(100); // Ensure different timestamps

        String backupPath = backupService.createBackupAndCleanup();

        assertTrue(Files.exists(Path.of(backupPath).resolve("mcserver-backup.tar.gz")),
                "The new backup archive should exist");
        assertFalse(Files.exists(older), "The older backup should have been pruned");
    }

    @Test
    @DisplayName("Should format file size correctly")
    void shouldFormatFileSize() {
        assertEquals("500B", ReflectionTestUtils.invokeMethod(backupService, "formatFileSize", 500L));
        assertEquals("1.5K", ReflectionTestUtils.invokeMethod(backupService, "formatFileSize", 1536L));
        assertEquals("2.0M", ReflectionTestUtils.invokeMethod(backupService, "formatFileSize", 2L * 1024 * 1024));
        assertEquals("1.5G", ReflectionTestUtils.invokeMethod(backupService, "formatFileSize", 
            (long)(1.5 * 1024 * 1024 * 1024)));
    }

    @Test
    @DisplayName("createBackup should create a tar.gz archive from the source directory")
    void shouldCreateBackupFromSourceDirectory() throws IOException, BackupException {
        // Create a source directory with some content
        Path sourceDir = tempDir.resolve("mcserver");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("world.dat"), "fake world data");
        Files.writeString(sourceDir.resolve("server.properties"), "server-port=25565");

        // Point the service at our temp dirs
        ReflectionTestUtils.setField(backupService, "sourceDirectory", sourceDir.toString());

        String backupPath = backupService.createBackup();

        assertNotNull(backupPath, "Backup path should not be null");
        Path archive = Path.of(backupPath).resolve("mcserver-backup.tar.gz");
        assertTrue(Files.exists(archive), "Backup archive should exist at " + archive);
        assertTrue(Files.size(archive) > 0, "Backup archive should not be empty");
    }

    @Test
    @DisplayName("createBackup should throw BackupException when source directory is missing")
    void shouldThrowWhenSourceDirectoryMissing() {
        Path nonExistentSource = tempDir.resolve("no-mcserver");
        ReflectionTestUtils.setField(backupService, "sourceDirectory", nonExistentSource.toString());

        assertThrows(BackupException.class, () -> backupService.createBackup());
    }

    @Test
    @DisplayName("createBackup should throw BackupException when source directory is empty")
    void shouldThrowWhenSourceDirectoryEmpty() throws IOException {
        Path emptySource = tempDir.resolve("empty-mcserver");
        Files.createDirectories(emptySource);
        ReflectionTestUtils.setField(backupService, "sourceDirectory", emptySource.toString());

        assertThrows(BackupException.class, () -> backupService.createBackup());
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns false for blank source directory")
    void shouldReturnFalseForBlankSourceDirectory() {
        ReflectionTestUtils.setField(backupService, "sourceDirectory", "   ");

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertFalse(available);
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns false for relative source directory path")
    void shouldReturnFalseForRelativeSourceDirectory() {
        ReflectionTestUtils.setField(backupService, "sourceDirectory", "relative/path");

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertFalse(available);
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns false for directory containing only subdirectories")
    void shouldReturnFalseForDirectoryWithOnlySubdirectories() throws IOException {
        Path dirWithSubdirs = tempDir.resolve("only-subdirs");
        Files.createDirectories(dirWithSubdirs.resolve("subdir1"));
        Files.createDirectories(dirWithSubdirs.resolve("subdir2"));
        ReflectionTestUtils.setField(backupService, "sourceDirectory", dirWithSubdirs.toString());

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertFalse(available);
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns true for directory with file in subdirectory")
    void shouldReturnTrueForDirectoryWithFileInSubdirectory() throws IOException {
        Path nestedSrc = tempDir.resolve("nested-src");
        Path subDir = nestedSrc.resolve("world");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("level.dat"), "fake level data");
        ReflectionTestUtils.setField(backupService, "sourceDirectory", nestedSrc.toString());

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertTrue(available);
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns false for non-existent directory")
    void shouldReturnFalseForNonExistentSourceDirectory() {
        Path nonExistent = tempDir.resolve("no-such-src");
        ReflectionTestUtils.setField(backupService, "sourceDirectory", nonExistent.toString());

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertFalse(available);
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns false for empty directory")
    void shouldReturnFalseForEmptySourceDirectory() throws IOException {
        Path emptyDir = tempDir.resolve("empty-src");
        Files.createDirectories(emptyDir);
        ReflectionTestUtils.setField(backupService, "sourceDirectory", emptyDir.toString());

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertFalse(available);
    }

    @Test
    @DisplayName("checkSourceDirectoryAvailable returns true for directory with content")
    void shouldReturnTrueForNonEmptySourceDirectory() throws IOException {
        Path srcDir = tempDir.resolve("nonempty-src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("file.txt"), "data");
        ReflectionTestUtils.setField(backupService, "sourceDirectory", srcDir.toString());

        boolean available = (boolean) ReflectionTestUtils.invokeMethod(
                backupService, "checkSourceDirectoryAvailable");
        assertTrue(available);
    }

    @Test
    @DisplayName("Should handle alert sending gracefully when disabled")
    void shouldHandleDisabledAlerts() {
        // Alerts are disabled in test properties
        // This should not throw an exception
        assertDoesNotThrow(() -> 
            ReflectionTestUtils.invokeMethod(backupService, "sendAlert", 
                "Test", "Message", "INFO", false));
    }

    @Test
    @DisplayName("getLatestBackupStatus returns null when backup directory does not exist")
    void shouldReturnNullWhenBackupDirectoryMissing() throws IOException {
        Path nonExistent = tempDir.resolve("no-such-dir");
        ReflectionTestUtils.setField(backupService, "backupDirectory", nonExistent.toString());

        assertNull(backupService.getLatestBackupStatus());
    }

    @Test
    @DisplayName("getLatestBackupStatus returns null when no backup folders exist")
    void shouldReturnNullWhenNoBackupFolders() {
        // tempDir exists but contains no backup-* subdirectories
        assertNull(backupService.getLatestBackupStatus());
    }

    @Test
    @DisplayName("getLatestBackupStatus returns successful status when backup file is present")
    void shouldReturnSuccessStatusWhenBackupFilePresent() throws IOException {
        Path backupDir = tempDir.resolve("backup-20240101-020000");
        Files.createDirectories(backupDir);
        Files.writeString(backupDir.resolve("mcserver-backup.tar.gz"), "fake-archive");

        BackupService.LatestBackupStatus status = backupService.getLatestBackupStatus();

        assertNotNull(status);
        assertTrue(status.success());
        assertEquals(backupDir.toString(), status.backupPath());
        assertNotNull(status.timestamp());
    }

    @Test
    @DisplayName("getLatestBackupStatus returns failed status when backup file is missing")
    void shouldReturnFailedStatusWhenBackupFileMissing() throws IOException {
        Path backupDir = tempDir.resolve("backup-20240101-030000");
        Files.createDirectories(backupDir);
        // No mcserver-backup.tar.gz

        BackupService.LatestBackupStatus status = backupService.getLatestBackupStatus();

        assertNotNull(status);
        assertFalse(status.success());
        assertNull(status.backupPath());
    }

    @Test
    @DisplayName("getLatestBackupStatus picks the newest backup folder")
    void shouldPickNewestBackupFolder() throws IOException {
        Path older = tempDir.resolve("backup-20240101-020000");
        Path newer = tempDir.resolve("backup-20240102-020000");
        Files.createDirectories(older);
        Files.createDirectories(newer);
        Files.writeString(older.resolve("mcserver-backup.tar.gz"), "old-archive");
        Files.writeString(newer.resolve("mcserver-backup.tar.gz"), "new-archive");

        BackupService.LatestBackupStatus status = backupService.getLatestBackupStatus();

        assertNotNull(status);
        assertTrue(status.success());
        assertEquals(newer.toString(), status.backupPath());
    }
}
