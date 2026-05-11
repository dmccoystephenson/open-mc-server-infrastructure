package com.openmc.webapp.repository.jpa;

import com.openmc.webapp.entity.ActivityTrackerSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ActivityTrackerSnapshotJpaRepository extends JpaRepository<ActivityTrackerSnapshotEntity, Long> {

    List<ActivityTrackerSnapshotEntity> findByTimestampAfterOrderByTimestampDesc(Instant cutoff);

    @Query("SELECT e.timestamp FROM ActivityTrackerSnapshotEntity e WHERE e.timestamp > :cutoff")
    Set<Instant> findTimestampsAfter(Instant cutoff);

    void deleteByTimestampBefore(Instant cutoff);
}
