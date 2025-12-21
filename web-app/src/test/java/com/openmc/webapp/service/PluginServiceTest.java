package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginService Tests")
class PluginServiceTest {
    
    @TempDir
    Path tempDir;
    
    private PluginService pluginService;
    private ServerConfig serverConfig;
    
    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setPluginsDirectory(tempDir.toString());
        pluginService = new PluginService(serverConfig);
    }
    
    /**
     * Creates a valid minimal JAR file content for testing
     */
    private byte[] createValidJarContent() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        
        try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {
            // Add a simple entry
            ZipEntry entry = new ZipEntry("test.txt");
            jos.putNextEntry(entry);
            jos.write("test".getBytes());
            jos.closeEntry();
        }
        
        return baos.toByteArray();
    }
    
    @Test
    @DisplayName("Should list plugins in directory")
    void shouldListPluginsInDirectory() throws IOException {
        // Create some test plugin files
        Files.createFile(tempDir.resolve("plugin1.jar"));
        Files.createFile(tempDir.resolve("plugin2.jar"));
        Files.createFile(tempDir.resolve("readme.txt")); // Non-jar file
        
        List<String> plugins = pluginService.listPlugins();
        
        assertEquals(2, plugins.size());
        assertTrue(plugins.contains("plugin1.jar"));
        assertTrue(plugins.contains("plugin2.jar"));
        assertFalse(plugins.contains("readme.txt"));
    }
    
    @Test
    @DisplayName("Should return empty list when directory is empty")
    void shouldReturnEmptyListWhenDirectoryIsEmpty() {
        List<String> plugins = pluginService.listPlugins();
        
        assertTrue(plugins.isEmpty());
    }
    
    @Test
    @DisplayName("Should return empty list when directory does not exist")
    void shouldReturnEmptyListWhenDirectoryDoesNotExist() {
        serverConfig.setPluginsDirectory("/nonexistent/directory");
        
        List<String> plugins = pluginService.listPlugins();
        
        assertTrue(plugins.isEmpty());
    }
    
    @Test
    @DisplayName("Should upload valid jar file")
    void shouldUploadValidJarFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test-plugin.jar", 
            "application/java-archive", 
            createValidJarContent()
        );
        
        String result = pluginService.uploadPlugin(file);
        
        assertTrue(result.startsWith("Plugin uploaded successfully"));
        assertTrue(Files.exists(tempDir.resolve("test-plugin.jar")));
    }
    
    @Test
    @DisplayName("Should reject null file")
    void shouldRejectNullFile() {
        String result = pluginService.uploadPlugin(null);
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("No file provided"));
    }
    
    @Test
    @DisplayName("Should reject empty file")
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test-plugin.jar", 
            "application/java-archive", 
            new byte[0]
        );
        
        String result = pluginService.uploadPlugin(file);
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("No file provided"));
    }
    
    @Test
    @DisplayName("Should reject non-jar file")
    void shouldRejectNonJarFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "test-plugin.txt", 
            "text/plain", 
            "test content".getBytes()
        );
        
        String result = pluginService.uploadPlugin(file);
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("must be a .jar file"));
    }
    
    @Test
    @DisplayName("Should reject invalid JAR content")
    void shouldRejectInvalidJarContent() {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "fake-plugin.jar", 
            "application/java-archive", 
            "not a valid jar file content".getBytes()
        );
        
        String result = pluginService.uploadPlugin(file);
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("not a valid JAR file"));
    }
    
    @Test
    @DisplayName("Should reject file that already exists")
    void shouldRejectFileThatAlreadyExists() throws IOException {
        // Create existing file
        Files.createFile(tempDir.resolve("existing.jar"));
        
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "existing.jar", 
            "application/java-archive", 
            createValidJarContent()
        );
        
        String result = pluginService.uploadPlugin(file);
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("already exists"));
    }
    
    @Test
    @DisplayName("Should delete existing plugin")
    void shouldDeleteExistingPlugin() throws IOException {
        // Create a test file
        Path pluginPath = tempDir.resolve("test-plugin.jar");
        Files.createFile(pluginPath);
        assertTrue(Files.exists(pluginPath));
        
        String result = pluginService.deletePlugin("test-plugin.jar");
        
        assertTrue(result.startsWith("Plugin deleted successfully"));
        assertFalse(Files.exists(pluginPath));
    }
    
    @Test
    @DisplayName("Should reject delete with null filename")
    void shouldRejectDeleteWithNullFilename() {
        String result = pluginService.deletePlugin(null);
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("Filename is required"));
    }
    
    @Test
    @DisplayName("Should reject delete with empty filename")
    void shouldRejectDeleteWithEmptyFilename() {
        String result = pluginService.deletePlugin("   ");
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("Filename is required"));
    }
    
    @Test
    @DisplayName("Should reject delete of non-jar file")
    void shouldRejectDeleteOfNonJarFile() {
        String result = pluginService.deletePlugin("test.txt");
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("must be a .jar file"));
    }
    
    @Test
    @DisplayName("Should reject delete of non-existent file")
    void shouldRejectDeleteOfNonExistentFile() {
        String result = pluginService.deletePlugin("nonexistent.jar");
        
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("does not exist"));
    }
    
    @Test
    @DisplayName("Should sanitize filename on upload to prevent directory traversal")
    void shouldSanitizeFilenameOnUpload() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", 
            "../../../evil.jar", 
            "application/java-archive", 
            createValidJarContent()
        );
        
        String result = pluginService.uploadPlugin(file);
        
        // Should be rejected due to path separators in filename
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("Invalid filename"));
        assertFalse(Files.exists(tempDir.resolve("evil.jar")));
    }
    
    @Test
    @DisplayName("Should reject directory traversal attempt on delete")
    void shouldRejectDirectoryTraversalOnDelete() throws IOException {
        // Create a test file in the temp directory
        Path pluginPath = tempDir.resolve("test.jar");
        Files.createFile(pluginPath);
        
        // Try to delete using path traversal - should be rejected
        String result = pluginService.deletePlugin("../test.jar");
        
        // File should still exist because path traversal was rejected
        assertTrue(Files.exists(pluginPath));
        assertTrue(result.startsWith("Error"));
    }
}
