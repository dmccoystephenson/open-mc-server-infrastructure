package com.openmc.webapp.repository;

import com.openmc.webapp.model.ActivityTrackerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for ActivityTrackerSnapshot entities.
 */
public interface ActivityTrackerSnapshotRepository extends JpaRepository<ActivityTrackerSnapshot, Long> {

    /**
     * Find all snapshots with timestamp after the given cutoff, ordered by timestamp descending.
     * Uses JOIN FETCH to eagerly load leaderboard entries and avoid N+1 queries.
     */
    @Query("SELECT DISTINCT s FROM ActivityTrackerSnapshot s LEFT JOIN FETCH s.leaderboard WHERE s.timestamp > :cutoff ORDER BY s.timestamp DESC")
    List<ActivityTrackerSnapshot> findByTimestampAfterOrderByTimestampDesc(@Param("cutoff") Instant cutoff);

    /**
     * Delete all snapshots with timestamp before the given cutoff.
     */
    void deleteByTimestampBefore(Instant cutoff);

    /**
     * Check if any snapshots exist in the table.
     */
    boolean existsByIdIsNotNull();
}
