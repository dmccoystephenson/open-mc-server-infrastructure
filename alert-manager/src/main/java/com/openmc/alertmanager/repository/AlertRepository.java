package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.model.AlertRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for AlertRecord entities.
 */
public interface AlertRepository extends JpaRepository<AlertRecord, Long> {

    /**
     * Maximum number of alerts to retain.
     */
    int MAX_STORED_ALERTS = 100;

    /**
     * Find the most recent alerts, ordered by receivedAt descending.
     */
    List<AlertRecord> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
