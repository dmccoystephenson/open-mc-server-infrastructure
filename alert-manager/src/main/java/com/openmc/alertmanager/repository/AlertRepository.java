package com.openmc.alertmanager.repository;

import com.openmc.alertmanager.model.AlertRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for AlertRecord entities.
 */
public interface AlertRepository extends JpaRepository<AlertRecord, Long> {

    /**
     * Maximum number of alerts returned by a single query.
     */
    int MAX_QUERY_LIMIT = 100;

    /**
     * Find the most recent alerts, ordered by receivedAt descending.
     */
    List<AlertRecord> findAllByOrderByReceivedAtDesc(Pageable pageable);

    /**
     * Delete all records received before the given cutoff.
     */
    void deleteByReceivedAtBefore(Instant cutoff);

    /**
     * Check if any records exist in the table.
     */
    boolean existsByIdIsNotNull();
}
