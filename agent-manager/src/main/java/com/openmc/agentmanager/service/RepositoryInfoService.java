package com.openmc.agentmanager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service that provides information about the OMCSI repository and its contents.
 * Loads a structured knowledge base from repository-info.json at startup and
 * serves topic-specific or full repository information to the AI agent.
 */
@Slf4j
@Service
public class RepositoryInfoService {

    private static final String RESOURCE_PATH = "repository-info.json";

    private final ObjectMapper objectMapper;
    private Map<String, Object> repositoryInfo;

    public RepositoryInfoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadRepositoryInfo() {
        try (InputStream is = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            repositoryInfo = objectMapper.readValue(is, new TypeReference<>() {});
            log.info("Loaded repository info with {} top-level topics", repositoryInfo.size());
        } catch (IOException e) {
            log.error("Failed to load repository info from {}: {}", RESOURCE_PATH, e.getMessage(), e);
            repositoryInfo = Map.of("error", "Repository information is not available.");
        }
    }

    /**
     * Get repository information for a specific topic.
     *
     * @param topic the topic to query (e.g. "overview", "services", "getting_started").
     *              If null or blank, returns all repository information.
     * @return JSON string containing the requested repository information
     */
    public String getRepositoryInfo(String topic) {
        log.info("Getting repository info for topic: {}", topic == null ? "all" : topic);
        try {
            if (topic == null || topic.isBlank()) {
                return objectMapper.writeValueAsString(repositoryInfo);
            }

            String normalizedTopic = topic.trim().toLowerCase().replace(" ", "_").replace("-", "_");
            Object topicData = repositoryInfo.get(normalizedTopic);

            if (topicData != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("topic", normalizedTopic);
                result.put("data", topicData);
                return objectMapper.writeValueAsString(result);
            }

            // Topic not found — return available topics
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "Topic '" + topic + "' not found.");
            result.put("available_topics", repositoryInfo.get("topics"));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize repository info for topic '{}': {}", topic, e.getMessage(), e);
            return "{\"error\":\"Failed to retrieve repository information.\"}";
        }
    }
}
