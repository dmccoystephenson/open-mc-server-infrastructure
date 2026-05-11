package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.entity.AlertRecordEntity;
import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertLevel;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.repository.jpa.AlertRecordJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Persistent store for recent alerts backed by PostgreSQL.
 * Retains up to {@link #MAX_STORED_ALERTS} most recent entries in memory for fast reads
 * and persists every write to the database so alerts survive restarts.
 */
@Slf4j
@Component
public class AlertRepository {

    public static final int MAX_STORED_ALERTS = 100;

    private final Deque<AlertRecord> recentAlerts = new ConcurrentLinkedDeque<>();
    private final AlertRecordJpaRepository jpaRepository;

    public AlertRepository(AlertRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        loadFromDatabase();
    }

    /**
     * Store an alert in memory and persist it to the database.
     * Synchronized to prevent interleaved mutations under concurrency.
     */
    @Transactional
    public synchronized void store(Alert alert) {
        Instant receivedAt = Instant.now();
        AlertRecord record = AlertRecord.builder()
                .title(alert.getTitle())
                .message(alert.getMessage())
                .level(alert.getLevel())
                .source(alert.getSource())
                .receivedAt(receivedAt)
                .build();

        jpaRepository.save(toEntity(record));

        recentAlerts.addFirst(record);
        while (recentAlerts.size() > MAX_STORED_ALERTS) {
            recentAlerts.removeLast();
        }
    }

    /**
     * Return the most recent alerts, newest first.
     *
     * @param limit maximum number of records to return
     * @return list of recent alert records
     */
    public List<AlertRecord> getRecent(int limit) {
        List<AlertRecord> result = new ArrayList<>();
        int count = 0;
        for (AlertRecord record : recentAlerts) {
            if (count >= limit) break;
            result.add(record);
            count++;
        }
        return result;
    }

    private void loadFromDatabase() {
        try {
            List<AlertRecordEntity> entities = jpaRepository.findTopByOrderByReceivedAtDesc(
                    PageRequest.of(0, MAX_STORED_ALERTS));
            for (AlertRecordEntity e : entities) {
                recentAlerts.addLast(toModel(e));
            }
            log.info("Loaded {} alert records from database", recentAlerts.size());
        } catch (Exception e) {
            log.error("Failed to load alert history from database: {}", e.getMessage());
        }
    }

    private AlertRecordEntity toEntity(AlertRecord r) {
        String level = r.getLevel() != null ? r.getLevel().name() : null;
        return new AlertRecordEntity(r.getTitle(), r.getMessage(), level, r.getSource(), r.getReceivedAt());
    }

    private AlertRecord toModel(AlertRecordEntity e) {
        AlertLevel level = null;
        if (e.getLevel() != null) {
            try {
                level = AlertLevel.valueOf(e.getLevel());
            } catch (IllegalArgumentException ex) {
                log.warn("Unknown alert level '{}', defaulting to INFO", e.getLevel());
                level = AlertLevel.INFO;
            }
        }
        return AlertRecord.builder()
                .title(e.getTitle())
                .message(e.getMessage())
                .level(level)
                .source(e.getSource())
                .receivedAt(e.getReceivedAt())
                .build();
    }
}
