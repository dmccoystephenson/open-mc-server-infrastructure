package com.openmc.webapp.repository;

import com.openmc.webapp.model.RetrievalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for RetrievalRecord entities.
 */
public interface RetrievalRecordRepository extends JpaRepository<RetrievalRecord, Long> {

    /**
     * Find all records with timestamp after the given cutoff, ordered by timestamp descending.
     */
    List<RetrievalRecord> findByTimestampAfterOrderByTimestampDesc(Instant cutoff);

    /**
     * Delete all records with timestamp before the given cutoff.
     */
    void deleteByTimestampBefore(Instant cutoff);

    /**
     * Check if any records exist in the table.
     */
    boolean existsByIdIsNotNull();
}
