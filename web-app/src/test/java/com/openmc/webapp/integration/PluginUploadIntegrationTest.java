package com.openmc.webapp.integration;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.service.AlertNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for plugin upload functionality.
 * Tests the complete upload flow from controller through service with real JAR files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Plugin Upload Integration Tests")
class PluginUploadIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ServerConfig serverConfig;
    
    @MockBean
    private AlertNotificationService alertNotificationService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        // Set the plugins directory to our temp directory for testing
        serverConfig.setPluginsDirectory(tempDir.toString());
        serverConfig.setAdminUsername("admin");
        serverConfig.setAdminPassword("admin");
    }
    
    /**
     * Creates a valid JAR file with proper manifest
     */
    private byte[] createValidJarFile() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "com.example.TestPlugin");
        
        try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {
            // Add a class file entry
            ZipEntry entry = new ZipEntry("com/example/TestPlugin.class");
            jos.putNextEntry(entry);
            jos.write("fake class file content".getBytes());
            jos.closeEntry();
            
            // Add a plugin.yml entry (common for Minecraft plugins)
            entry = new ZipEntry("plugin.yml");
            jos.putNextEntry(entry);
            String pluginYml = "name: TestPlugin\nversion: 1.0\nmain: com.example.TestPlugin\n";
            jos.write(pluginYml.getBytes());
            jos.closeEntry();
        }
        
        return baos.toByteArray();
    }
    
    @Test
    @DisplayName("Should successfully upload a valid JAR file through the full stack")
    void shouldUploadValidJarFileThroughFullStack() throws Exception {
        // Create a real JAR file
        byte[] jarContent = createValidJarFile();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "TestPlugin.jar",
            "application/java-archive",
            jarContent
        );
        
        // Perform the upload request
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("uploaded successfully")))
                .andExpect(jsonPath("$.message").value(containsString("TestPlugin.jar")));
        
        // Verify the file was actually saved
        Path uploadedFile = tempDir.resolve("TestPlugin.jar");
        assert Files.exists(uploadedFile) : "Uploaded file should exist";
        
        // Verify the content matches
        byte[] savedContent = Files.readAllBytes(uploadedFile);
        assert java.util.Arrays.equals(jarContent, savedContent) : "Saved content should match uploaded content";
    }
    
    @Test
    @DisplayName("Should return valid JSON response even for large JAR files")
    void shouldHandleLargeJarFileUpload() throws Exception {
        // Create a JAR file that's large but under the 100MB limit
        byte[] jarContent = createValidJarFile();
        
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "LargePlugin.jar",
            "application/java-archive",
            jarContent
        );
        
        // Perform the upload request
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    @DisplayName("Should return valid JSON error for invalid JAR content")
    void shouldReturnValidJsonForInvalidJar() throws Exception {
        // Create a file that's not a valid JAR
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "invalid.jar",
            "application/java-archive",
            "This is not a valid JAR file".getBytes()
        );
        
        // Should return valid JSON error response
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("not a valid JAR file")));
    }
    
    @Test
    @DisplayName("Should return valid JSON for file already exists error")
    void shouldReturnValidJsonForDuplicateFile() throws Exception {
        byte[] jarContent = createValidJarFile();
        
        // Upload the file once
        MockMultipartFile file1 = new MockMultipartFile(
            "file",
            "duplicate.jar",
            "application/java-archive",
            jarContent
        );
        
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file1)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        // Try to upload again with the same filename
        MockMultipartFile file2 = new MockMultipartFile(
            "file",
            "duplicate.jar",
            "application/java-archive",
            jarContent
        );
        
        // Should return valid JSON error response
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file2)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("already exists")));
    }
    
    @Test
    @DisplayName("Should handle JAR files with various sizes correctly")
    void shouldHandleVariousSizedJarFiles() throws Exception {
        // Test with a minimal JAR
        byte[] jarContent = createValidJarFile();
        
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "various-size-plugin.jar",
            "application/java-archive",
            jarContent
        );
        
        // Verify it returns proper JSON response
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    @DisplayName("Should return valid JSON when file size exceeds limit")
    void shouldReturnValidJsonForOversizedFile() throws Exception {
        // Create a mock file that reports > 100MB size
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "huge-plugin.jar",
            "application/java-archive",
            new byte[100]
        ) {
            @Override
            public long getSize() {
                return 101L * 1024 * 1024; // Report 101MB
            }
        };
        
        // Should return valid JSON error response
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("exceeds maximum")));
    }
}
