package com.openmc.upgrademanager.service;

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
    "version.check.enabled=false",
    "alerts.version.check=false"
})
@DisplayName("MinecraftVersionService Tests")
class MinecraftVersionServiceTest {

    @Autowired
    private MinecraftVersionService versionService;

    @MockBean
    private RestTemplate restTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Set temp paths for testing
    }

    @Test
    @DisplayName("Should read current version from .env file")
    void shouldReadCurrentVersion() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MINECRAFT_VERSION=1.21.10\nOTHER_VAR=value\n");
        
        ReflectionTestUtils.setField(versionService, "envFilePath", envFile.toString());
        
        String version = versionService.getCurrentVersion();
        
        assertEquals("1.21.10", version);
    }

    @Test
    @DisplayName("Should return unknown when .env file does not exist")
    void shouldReturnUnknownWhenEnvFileNotFound() {
        Path nonExistentFile = tempDir.resolve("nonexistent.env");
        ReflectionTestUtils.setField(versionService, "envFilePath", nonExistentFile.toString());
        
        String version = versionService.getCurrentVersion();
        
        assertEquals("unknown", version);
    }

    @Test
    @DisplayName("Should return unknown when MINECRAFT_VERSION not in .env")
    void shouldReturnUnknownWhenVersionNotInEnv() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "OTHER_VAR=value\n");
        
        ReflectionTestUtils.setField(versionService, "envFilePath", envFile.toString());
        
        String version = versionService.getCurrentVersion();
        
        assertEquals("unknown", version);
    }

    @Test
    @DisplayName("Should update version in .env file")
    void shouldUpdateVersionInEnv() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MINECRAFT_VERSION=1.21.10\nOTHER_VAR=value\n");
        
        ReflectionTestUtils.setField(versionService, "envFilePath", envFile.toString());
        
        versionService.updateEnvVersion("1.21.11");
        
        String content = Files.readString(envFile);
        assertTrue(content.contains("MINECRAFT_VERSION=1.21.11"));
        assertTrue(content.contains("OTHER_VAR=value"));
    }

    @Test
    @DisplayName("Should add version to .env if not present")
    void shouldAddVersionToEnvIfNotPresent() throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "OTHER_VAR=value\n");
        
        ReflectionTestUtils.setField(versionService, "envFilePath", envFile.toString());
        
        versionService.updateEnvVersion("1.21.11");
        
        String content = Files.readString(envFile);
        assertTrue(content.contains("MINECRAFT_VERSION=1.21.11"));
        assertTrue(content.contains("OTHER_VAR=value"));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent .env file")
    void shouldThrowExceptionWhenUpdatingNonExistentEnv() {
        Path nonExistentFile = tempDir.resolve("nonexistent.env");
        ReflectionTestUtils.setField(versionService, "envFilePath", nonExistentFile.toString());
        
        assertThrows(IOException.class, () -> {
            versionService.updateEnvVersion("1.21.11");
        });
    }
}
