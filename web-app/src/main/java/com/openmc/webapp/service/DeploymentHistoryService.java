package com.openmc.webapp.service;

import com.openmc.webapp.model.DeploymentRecord;
import com.openmc.webapp.repository.DeploymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for managing deployment history records.
 */
@Service
public class DeploymentHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentHistoryService.class);

    private final DeploymentRecordRepository repository;

    public DeploymentHistoryService(DeploymentRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Record a new deployment event.
     * Synchronized to prevent lost updates from concurrent notifications.
     */
    public synchronized void recordDeployment(String pluginName, String status, String source,
                                  String branch, String repoUrl, String message) {
        DeploymentRecord record = new DeploymentRecord(
                Instant.now(), pluginName, status, source, branch, repoUrl, message);

        List<DeploymentRecord> history = new ArrayList<>(repository.findAll());
        history.add(record);
        repository.save(history);

        logger.info("Recorded deployment: plugin={}, status={}, source={}", pluginName, status, source);
    }

    /**
     * Get all deployment records, sorted by timestamp descending (most recent first).
     */
    public List<DeploymentRecord> getDeploymentHistory() {
        List<DeploymentRecord> history = new ArrayList<>(repository.findAll());
        history.sort(Comparator.comparing(DeploymentRecord::getTimestamp).reversed());
        return history;
    }
}
