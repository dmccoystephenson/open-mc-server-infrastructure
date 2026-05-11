package com.openmc.webapp.repository.jpa;

import com.openmc.webapp.entity.DeploymentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface DeploymentRecordJpaRepository extends JpaRepository<DeploymentRecordEntity, Long> {

    List<DeploymentRecordEntity> findByTimestampAfterOrderByTimestampDesc(Instant cutoff);

    @Query("SELECT e.timestamp FROM DeploymentRecordEntity e WHERE e.timestamp > :cutoff")
    Set<Instant> findTimestampsAfter(Instant cutoff);

    void deleteByTimestampBefore(Instant cutoff);
}
