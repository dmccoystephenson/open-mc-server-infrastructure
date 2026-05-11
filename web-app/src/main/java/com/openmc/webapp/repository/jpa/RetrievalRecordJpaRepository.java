package com.openmc.webapp.repository.jpa;

import com.openmc.webapp.entity.RetrievalRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface RetrievalRecordJpaRepository extends JpaRepository<RetrievalRecordEntity, Long> {

    List<RetrievalRecordEntity> findByTimestampAfterOrderByTimestampDesc(Instant cutoff);

    @Query("SELECT e.timestamp FROM RetrievalRecordEntity e WHERE e.timestamp > :cutoff")
    Set<Instant> findTimestampsAfter(Instant cutoff);

    void deleteByTimestampBefore(Instant cutoff);
}
