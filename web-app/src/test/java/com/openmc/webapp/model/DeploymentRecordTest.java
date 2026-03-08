package com.openmc.webapp.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeploymentRecord Tests")
class DeploymentRecordTest {

    @Test
    @DisplayName("Should create deployment record with all fields")
    void shouldCreateDeploymentRecordWithAllFields() {
        Instant timestamp = Instant.now();
        DeploymentRecord record = new DeploymentRecord(
                timestamp, "MyPlugin.jar", "SUCCESS", "automated",
                "main", "https://github.com/org/repo", "Plugin deployed successfully");

        assertEquals(timestamp, record.getTimestamp());
        assertEquals("MyPlugin.jar", record.getPluginName());
        assertEquals("SUCCESS", record.getStatus());
        assertEquals("automated", record.getSource());
        assertEquals("main", record.getBranch());
        assertEquals("https://github.com/org/repo", record.getRepoUrl());
        assertEquals("Plugin deployed successfully", record.getMessage());
    }

    @Test
    @DisplayName("Should create deployment record with null optional fields")
    void shouldCreateDeploymentRecordWithNullOptionalFields() {
        Instant timestamp = Instant.now();
        DeploymentRecord record = new DeploymentRecord(
                timestamp, "MyPlugin.jar", "FAILURE", "webapp",
                null, null, "Upload failed");

        assertEquals(timestamp, record.getTimestamp());
        assertEquals("MyPlugin.jar", record.getPluginName());
        assertEquals("FAILURE", record.getStatus());
        assertEquals("webapp", record.getSource());
        assertNull(record.getBranch());
        assertNull(record.getRepoUrl());
        assertEquals("Upload failed", record.getMessage());
    }

    @Test
    @DisplayName("Should preserve timestamp")
    void shouldPreserveTimestamp() {
        Instant before = Instant.now();
        DeploymentRecord record = new DeploymentRecord(
                before, "MyPlugin.jar", "SUCCESS", "automated",
                null, null, null);
        Instant after = Instant.now();

        assertEquals(before, record.getTimestamp());
        assertFalse(record.getTimestamp().isBefore(before));
        assertFalse(record.getTimestamp().isAfter(after));
    }
}
