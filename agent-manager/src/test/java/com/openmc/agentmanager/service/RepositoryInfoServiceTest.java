package com.openmc.agentmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepositoryInfoService Tests")
class RepositoryInfoServiceTest {

    private RepositoryInfoService repositoryInfoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        createRepoStructure();
        repositoryInfoService = new RepositoryInfoService(objectMapper);
        ReflectionTestUtils.setField(repositoryInfoService, "repositoryRoot", tempDir.toString());
        repositoryInfoService.scanRepository();
    }

    private void createRepoStructure() throws IOException {
        Files.writeString(tempDir.resolve("README.md"),
                "# Test Project\n\nA test project for unit testing.\n\n## Features\n\n- Feature 1\n");
        Files.writeString(tempDir.resolve("LICENSE"), "MIT License\n\nCopyright ...");
        Files.writeString(tempDir.resolve("compose.yml"),
                """
                services:
                  service-a:
                    build:
                      context: ./service-a
                    ports:
                      - "8080:8080"
                    container_name: test-service-a
                  service-b:
                    build: ./service-b
                    container_name: test-service-b
                    depends_on:
                      - service-a
                volumes:
                  app-data:
                    external: false
                """);
        Files.writeString(tempDir.resolve("sample.env"),
                "# Server Configuration\nSERVER_PORT=8080\nDEBUG=false\n");
        Files.writeString(tempDir.resolve("up.sh"),
                "#!/bin/bash\n# Start all services\ndocker compose up -d\n");
        Files.writeString(tempDir.resolve("down.sh"),
                "#!/bin/bash\n# Stop all services\ndocker compose down\n");
        Files.writeString(tempDir.resolve("SELF-HOSTING.md"),
                "# Self-Hosting Guide\n\nHow to self-host this project.\n\n## Network Setup\n\nOpen port 8080.\n");
        Files.writeString(tempDir.resolve("CONTRIBUTING.md"),
                "# Contributing\n\nHow to contribute to this project.\n");

        Files.createDirectories(tempDir.resolve("scripts"));
        Files.writeString(tempDir.resolve("scripts/ci-local.sh"),
                "#!/bin/bash\n# Run local CI validation\necho 'Running CI'\n");

        Files.createDirectories(tempDir.resolve("service-a"));
        Files.writeString(tempDir.resolve("service-a/Dockerfile"), "FROM java:21\nCOPY . /app\n");
        Files.writeString(tempDir.resolve("service-a/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(tempDir.resolve("service-a/README.md"),
                "# Service A\n\nService A handles data processing.\n\n## API\n\n- GET /api/data\n");

        Files.createDirectories(tempDir.resolve("service-b"));
        Files.writeString(tempDir.resolve("service-b/Dockerfile"), "FROM nginx\nCOPY . /etc/nginx\n");
        Files.writeString(tempDir.resolve("service-b/README.md"),
                "# Service B\n\nService B is the reverse proxy.\n");

        Files.createDirectories(tempDir.resolve(".github/workflows"));
        Files.writeString(tempDir.resolve(".github/workflows/ci.yml"),
                "name: CI Pipeline\non: push\njobs:\n  build:\n    runs-on: ubuntu-latest\n");
    }

    // ─── Overview Tests ────────────────────────────────────────

    @Test
    @DisplayName("Should discover project name from README.md")
    void shouldDiscoverProjectNameFromReadme() {
        String result = repositoryInfoService.getRepositoryInfo("overview");
        assertNotNull(result);
        assertTrue(result.contains("Test Project"));
    }

    @Test
    @DisplayName("Should discover project description from README.md")
    void shouldDiscoverProjectDescriptionFromReadme() {
        String result = repositoryInfoService.getRepositoryInfo("overview");
        assertNotNull(result);
        assertTrue(result.contains("test project for unit testing"));
    }

    @Test
    @DisplayName("Should detect license type")
    void shouldDetectLicenseType() {
        String result = repositoryInfoService.getRepositoryInfo("overview");
        assertNotNull(result);
        assertTrue(result.contains("MIT"));
    }

    @Test
    @DisplayName("Should list documentation files")
    void shouldListDocumentationFiles() {
        String result = repositoryInfoService.getRepositoryInfo("overview");
        assertNotNull(result);
        assertTrue(result.contains("CONTRIBUTING.md"));
        assertTrue(result.contains("SELF-HOSTING.md"));
    }

    // ─── Services Tests ────────────────────────────────────────

    @Test
    @DisplayName("Should discover services with Dockerfiles")
    void shouldDiscoverServicesWithDockerfiles() {
        String result = repositoryInfoService.getRepositoryInfo("services");
        assertNotNull(result);
        assertTrue(result.contains("service-a"));
        assertTrue(result.contains("service-b"));
    }

    @Test
    @DisplayName("Should read service descriptions from README files")
    void shouldReadServiceDescriptionsFromReadme() {
        String result = repositoryInfoService.getRepositoryInfo("services");
        assertNotNull(result);
        assertTrue(result.contains("data processing"));
        assertTrue(result.contains("reverse proxy"));
    }

    @Test
    @DisplayName("Should match compose ports to services")
    void shouldMatchComposePortsToServices() {
        String result = repositoryInfoService.getRepositoryInfo("services");
        assertNotNull(result);
        assertTrue(result.contains("8080"));
    }

    @Test
    @DisplayName("Should detect build tool")
    void shouldDetectBuildTool() {
        String result = repositoryInfoService.getRepositoryInfo("services");
        assertNotNull(result);
        assertTrue(result.contains("Gradle"));
    }

    // ─── Getting Started Tests ─────────────────────────────────

    @Test
    @DisplayName("Should build getting started from discovered files")
    void shouldBuildGettingStartedFromDiscoveredFiles() {
        String result = repositoryInfoService.getRepositoryInfo("getting_started");
        assertNotNull(result);
        assertTrue(result.contains("sample.env"));
        assertTrue(result.contains("up.sh"));
        assertTrue(result.contains("down.sh"));
    }

    @Test
    @DisplayName("Should include Docker as prerequisite")
    void shouldIncludeDockerAsPrerequisite() {
        String result = repositoryInfoService.getRepositoryInfo("getting_started");
        assertNotNull(result);
        assertTrue(result.contains("Docker"));
    }

    // ─── Architecture Tests ────────────────────────────────────

    @Test
    @DisplayName("Should parse compose.yml for architecture")
    void shouldParseComposeForArchitecture() {
        String result = repositoryInfoService.getRepositoryInfo("architecture");
        assertNotNull(result);
        assertTrue(result.contains("service-a"));
        assertTrue(result.contains("service-b"));
    }

    @Test
    @DisplayName("Should discover volumes from compose.yml")
    void shouldDiscoverVolumesFromCompose() {
        String result = repositoryInfoService.getRepositoryInfo("architecture");
        assertNotNull(result);
        assertTrue(result.contains("app-data"));
    }

    // ─── Scripts Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Should discover shell scripts")
    void shouldDiscoverShellScripts() {
        String result = repositoryInfoService.getRepositoryInfo("scripts");
        assertNotNull(result);
        assertTrue(result.contains("up.sh"));
        assertTrue(result.contains("down.sh"));
    }

    @Test
    @DisplayName("Should discover scripts in scripts/ directory")
    void shouldDiscoverScriptsInSubdirectory() {
        String result = repositoryInfoService.getRepositoryInfo("scripts");
        assertNotNull(result);
        assertTrue(result.contains("ci-local.sh"));
    }

    @Test
    @DisplayName("Should extract script descriptions from comments")
    void shouldExtractScriptDescriptions() {
        String result = repositoryInfoService.getRepositoryInfo("scripts");
        assertNotNull(result);
        assertTrue(result.contains("Start all services"));
        assertTrue(result.contains("Stop all services"));
    }

    // ─── Configuration Tests ───────────────────────────────────

    @Test
    @DisplayName("Should include sample.env content in configuration")
    void shouldIncludeSampleEnvContent() {
        String result = repositoryInfoService.getRepositoryInfo("configuration");
        assertNotNull(result);
        assertTrue(result.contains("SERVER_PORT"));
    }

    // ─── Self-Hosting Tests ────────────────────────────────────

    @Test
    @DisplayName("Should include self-hosting guide content")
    void shouldIncludeSelfHostingContent() {
        String result = repositoryInfoService.getRepositoryInfo("self_hosting");
        assertNotNull(result);
        assertTrue(result.contains("Self-Hosting"));
        assertTrue(result.contains("port 8080"));
    }

    // ─── CI/CD Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Should discover GitHub Actions workflows")
    void shouldDiscoverWorkflows() {
        String result = repositoryInfoService.getRepositoryInfo("ci_cd");
        assertNotNull(result);
        assertTrue(result.contains("ci.yml"));
        assertTrue(result.contains("GitHub Actions"));
    }

    @Test
    @DisplayName("Should extract workflow names")
    void shouldExtractWorkflowNames() {
        String result = repositoryInfoService.getRepositoryInfo("ci_cd");
        assertNotNull(result);
        assertTrue(result.contains("CI Pipeline"));
    }

    // ─── General Behavior Tests ────────────────────────────────

    @Test
    @DisplayName("Should return all info when topic is null")
    void shouldReturnAllInfoWhenTopicIsNull() {
        String result = repositoryInfoService.getRepositoryInfo(null);
        assertNotNull(result);
        assertTrue(result.contains("overview"));
        assertTrue(result.contains("services"));
        assertTrue(result.contains("getting_started"));
        assertTrue(result.contains("architecture"));
    }

    @Test
    @DisplayName("Should return all info when topic is blank")
    void shouldReturnAllInfoWhenTopicIsBlank() {
        String result = repositoryInfoService.getRepositoryInfo("  ");
        assertNotNull(result);
        assertTrue(result.contains("overview"));
        assertTrue(result.contains("services"));
    }

    @Test
    @DisplayName("Should handle topic with hyphens")
    void shouldHandleTopicWithHyphens() {
        String result = repositoryInfoService.getRepositoryInfo("getting-started");
        assertNotNull(result);
        assertTrue(result.contains("quick_start"));
    }

    @Test
    @DisplayName("Should handle topic with spaces")
    void shouldHandleTopicWithSpaces() {
        String result = repositoryInfoService.getRepositoryInfo("getting started");
        assertNotNull(result);
        assertTrue(result.contains("quick_start"));
    }

    @Test
    @DisplayName("Should handle topic case insensitivity")
    void shouldHandleTopicCaseInsensitivity() {
        String result = repositoryInfoService.getRepositoryInfo("OVERVIEW");
        assertNotNull(result);
        assertTrue(result.contains("Test Project"));
    }

    @Test
    @DisplayName("Should return error for unknown topic")
    void shouldReturnErrorForUnknownTopic() {
        String result = repositoryInfoService.getRepositoryInfo("nonexistent_topic");
        assertNotNull(result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("not found"));
        assertTrue(result.contains("available_topics"));
    }

    @Test
    @DisplayName("Should return valid JSON for all topics")
    void shouldReturnValidJsonForAllTopics() {
        String[] topics = {"overview", "services", "getting_started", "architecture",
                "scripts", "configuration", "self_hosting", "ci_cd"};
        for (String topic : topics) {
            String result = repositoryInfoService.getRepositoryInfo(topic);
            assertNotNull(result, "Result for '" + topic + "' should not be null");
            assertDoesNotThrow(() -> objectMapper.readTree(result),
                    "Result for '" + topic + "' should be valid JSON");
        }
    }

    @Test
    @DisplayName("Should return valid JSON for null topic")
    void shouldReturnValidJsonForNullTopic() {
        String result = repositoryInfoService.getRepositoryInfo(null);
        assertDoesNotThrow(() -> objectMapper.readTree(result));
    }

    // ─── Error Handling Tests ──────────────────────────────────

    @Test
    @DisplayName("Should handle missing repository root gracefully")
    void shouldHandleMissingRepositoryRootGracefully() {
        RepositoryInfoService service = new RepositoryInfoService(objectMapper);
        ReflectionTestUtils.setField(service, "repositoryRoot", "");
        service.scanRepository();

        String result = service.getRepositoryInfo(null);
        assertNotNull(result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("not configured"));
    }

    @Test
    @DisplayName("Should handle nonexistent repository root gracefully")
    void shouldHandleNonexistentRepositoryRootGracefully() {
        RepositoryInfoService service = new RepositoryInfoService(objectMapper);
        ReflectionTestUtils.setField(service, "repositoryRoot", "/nonexistent/path");
        service.scanRepository();

        String result = service.getRepositoryInfo(null);
        assertNotNull(result);
        assertTrue(result.contains("error"));
    }
}
