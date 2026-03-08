package com.openmc.webapp.repository;

import com.openmc.webapp.model.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for DeploymentRecord entities.
 */
public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, Long> {

    /**
     * Find all records with timestamp after the given cutoff, ordered by timestamp descending.
     */
    List<DeploymentRecord> findByTimestampAfterOrderByTimestampDesc(Instant cutoff);

    /**
     * Delete all records with timestamp before the given cutoff.
     */
    void deleteByTimestampBefore(Instant cutoff);

    /**
     * Check if any records exist in the table.
     */
    boolean existsByIdIsNotNull();
}
