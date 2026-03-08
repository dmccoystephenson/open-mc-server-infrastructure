package com.openmc.webapp.service;

import com.openmc.webapp.model.DeploymentRecord;
import com.openmc.webapp.repository.DeploymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service for managing deployment history records.
 */
@Service
public class DeploymentHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentHistoryService.class);
    private static final Duration RETENTION_PERIOD = Duration.ofDays(7);

    private final DeploymentRecordRepository repository;

    public DeploymentHistoryService(DeploymentRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Record a new deployment event.
     */
    public synchronized void recordDeployment(String pluginName, String status, String source,
                                  String branch, String repoUrl, String message) {
        DeploymentRecord record = new DeploymentRecord(
                Instant.now(), pluginName, status, source, branch, repoUrl, message);

        repository.save(record);

        logger.info("Recorded deployment: plugin={}, status={}, source={}", pluginName, status, source);
    }

    /**
     * Get all deployment records within the retention period, sorted by timestamp descending (most recent first).
     */
    public List<DeploymentRecord> getDeploymentHistory() {
        Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
        return repository.findByTimestampAfterOrderByTimestampDesc(cutoff);
    }
}
