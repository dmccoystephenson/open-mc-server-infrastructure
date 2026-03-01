package com.openmc.agentmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for calling the backup-manager REST API.
 */
@Slf4j
@Service
public class BackupManagerService {

    private final RestTemplate restTemplate;

    @Value("${backup.manager.url:http://backup-manager:8091}")
    private String backupManagerUrl;

    public BackupManagerService(@Qualifier("backupRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Trigger a manual backup.
     * @return response message from the backup manager
     */
    public String triggerBackup() {
        log.info("Calling backup-manager to trigger backup");
        String url = backupManagerUrl + "/api/backups/trigger";
        try {
            log.debug("Sending POST request to backup-manager: {}", url);
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String body = response.getBody();
            log.info("Backup manager trigger response: {} - {}", response.getStatusCode(), body);
            return body != null ? body : "Backup triggered";
        } catch (Exception e) {
            log.error("Failed to trigger backup via backup-manager at {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to trigger backup: " + e.getMessage(), e);
        }
    }
}
