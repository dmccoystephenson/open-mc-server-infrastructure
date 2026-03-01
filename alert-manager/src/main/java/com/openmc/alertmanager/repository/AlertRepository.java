package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory store for recent alerts.
 * Retains up to MAX_STORED_ALERTS most recent entries (newest first).
 */
@Component
public class AlertRepository {

    public static final int MAX_STORED_ALERTS = 100;

    private final Deque<AlertRecord> recentAlerts = new ConcurrentLinkedDeque<>();

    /**
     * Store an alert in the in-memory history.
     *
     * @param alert the alert to store
     */
    public void store(Alert alert) {
        recentAlerts.addFirst(AlertRecord.builder()
                .title(alert.getTitle())
                .message(alert.getMessage())
                .level(alert.getLevel())
                .source(alert.getSource())
                .receivedAt(Instant.now())
                .build());
        // Trim to max size
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
            if (count >= limit) {
                break;
            }
            result.add(record);
            count++;
        }
        return result;
    }
}
