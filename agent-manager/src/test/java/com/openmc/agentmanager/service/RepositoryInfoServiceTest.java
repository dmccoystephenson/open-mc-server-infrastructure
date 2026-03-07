package com.openmc.agentmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepositoryInfoService Tests")
class RepositoryInfoServiceTest {

    private RepositoryInfoService repositoryInfoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repositoryInfoService = new RepositoryInfoService(objectMapper);
        repositoryInfoService.loadRepositoryInfo();
    }

    @Test
    @DisplayName("Should load repository info on startup")
    void shouldLoadRepositoryInfoOnStartup() {
        String result = repositoryInfoService.getRepositoryInfo(null);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("overview"));
        assertTrue(result.contains("services"));
    }

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
    @DisplayName("Should return specific topic info for overview")
    void shouldReturnSpecificTopicInfoForOverview() {
        String result = repositoryInfoService.getRepositoryInfo("overview");

        assertNotNull(result);
        assertTrue(result.contains("Open MC Server Infrastructure"));
        assertTrue(result.contains("topic"));
        assertTrue(result.contains("overview"));
    }

    @Test
    @DisplayName("Should return specific topic info for services")
    void shouldReturnSpecificTopicInfoForServices() {
        String result = repositoryInfoService.getRepositoryInfo("services");

        assertNotNull(result);
        assertTrue(result.contains("minecraft-wrapper"));
        assertTrue(result.contains("web-app"));
        assertTrue(result.contains("backup-manager"));
        assertTrue(result.contains("alert-manager"));
        assertTrue(result.contains("agent-manager"));
    }

    @Test
    @DisplayName("Should return specific topic info for getting started")
    void shouldReturnSpecificTopicInfoForGettingStarted() {
        String result = repositoryInfoService.getRepositoryInfo("getting_started");

        assertNotNull(result);
        assertTrue(result.contains("quick_start"));
        assertTrue(result.contains("prerequisites"));
    }

    @Test
    @DisplayName("Should return specific topic info for architecture")
    void shouldReturnSpecificTopicInfoForArchitecture() {
        String result = repositoryInfoService.getRepositoryInfo("architecture");

        assertNotNull(result);
        assertTrue(result.contains("microservices"));
        assertTrue(result.contains("Docker"));
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
        assertTrue(result.contains("Open MC Server Infrastructure"));
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
    void shouldReturnValidJsonForAllTopics() throws Exception {
        String[] topics = {"overview", "services", "getting_started", "architecture",
                "scripts", "configuration", "self_hosting", "ci_cd"};

        for (String topic : topics) {
            String result = repositoryInfoService.getRepositoryInfo(topic);
            assertNotNull(result, "Result for topic '" + topic + "' should not be null");
            // Verify it's valid JSON by parsing it
            assertDoesNotThrow(() -> objectMapper.readTree(result),
                    "Result for topic '" + topic + "' should be valid JSON");
        }
    }

    @Test
    @DisplayName("Should return valid JSON for null topic")
    void shouldReturnValidJsonForNullTopic() throws Exception {
        String result = repositoryInfoService.getRepositoryInfo(null);
        assertDoesNotThrow(() -> objectMapper.readTree(result));
    }

    @Test
    @DisplayName("Should include self-hosting information")
    void shouldIncludeSelfHostingInformation() {
        String result = repositoryInfoService.getRepositoryInfo("self_hosting");

        assertNotNull(result);
        assertTrue(result.contains("25565"));
        assertTrue(result.contains("8080"));
    }

    @Test
    @DisplayName("Should include CI/CD information")
    void shouldIncludeCiCdInformation() {
        String result = repositoryInfoService.getRepositoryInfo("ci_cd");

        assertNotNull(result);
        assertTrue(result.contains("ci.yml"));
        assertTrue(result.contains("GitHub Actions"));
    }

    @Test
    @DisplayName("Should include scripts information")
    void shouldIncludeScriptsInformation() {
        String result = repositoryInfoService.getRepositoryInfo("scripts");

        assertNotNull(result);
        assertTrue(result.contains("up.sh"));
        assertTrue(result.contains("down.sh"));
    }
}
