package com.openmc.webapp.repository;

import com.openmc.webapp.model.ActivityTrackerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for ActivityTrackerSnapshot entities.
 */
public interface ActivityTrackerSnapshotRepository extends JpaRepository<ActivityTrackerSnapshot, Long> {

    /**
     * Find all snapshots with timestamp after the given cutoff, ordered by timestamp descending.
     */
    List<ActivityTrackerSnapshot> findByTimestampAfterOrderByTimestampDesc(Instant cutoff);

    /**
     * Delete all snapshots with timestamp before the given cutoff.
     */
    void deleteByTimestampBefore(Instant cutoff);
}
