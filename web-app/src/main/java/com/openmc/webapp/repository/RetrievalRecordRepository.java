package com.openmc.webapp.repository;

import com.openmc.webapp.entity.RetrievalRecordEntity;
import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.repository.jpa.RetrievalRecordJpaRepository;
import com.openmc.webapp.service.RconService.ResourceUsage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class RetrievalRecordRepository implements Repository<RetrievalRecord> {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final RetrievalRecordJpaRepository jpaRepository;

    public RetrievalRecordRepository(RetrievalRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(List<RetrievalRecord> entities) {
        Instant cutoff = Instant.now().minus(RETENTION);
        Set<Instant> existing = jpaRepository.findTimestampsAfter(cutoff);
        entities.stream()
                .filter(e -> e.getTimestamp().isAfter(cutoff))
                .filter(e -> !existing.contains(e.getTimestamp()))
                .forEach(e -> jpaRepository.save(toEntity(e)));
        jpaRepository.deleteByTimestampBefore(cutoff);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievalRecord> findAll() {
        Instant cutoff = Instant.now().minus(RETENTION);
        return jpaRepository.findByTimestampAfterOrderByTimestampDesc(cutoff)
                .stream()
                .map(this::toModel)
                .toList();
    }

    @Override
    @Transactional
    public void clear() {
        jpaRepository.deleteAll();
    }

    private RetrievalRecordEntity toEntity(RetrievalRecord m) {
        ResourceUsage ru = m.getResourceUsage();
        return new RetrievalRecordEntity(
                m.getTimestamp(), m.isSuccess(), m.getPlayerCount(),
                ru != null ? ru.getTps() : null,
                ru != null ? ru.getMemoryUsed() : null,
                ru != null ? ru.getMemoryMax() : null,
                ru != null ? ru.getMemoryFree() : null,
                ru != null ? ru.getMemoryUsedPercent() : null);
    }

    private RetrievalRecord toModel(RetrievalRecordEntity e) {
        ResourceUsage ru = null;
        if (e.getTps() != null || e.getMemoryUsed() != null) {
            ru = new ResourceUsage(e.getTps(), e.getMemoryUsed(), e.getMemoryMax(),
                    e.getMemoryFree(), e.getMemoryUsedPercent() != null ? e.getMemoryUsedPercent() : 0.0);
        }
        return new RetrievalRecord(e.getTimestamp(), e.isSuccess(), e.getPlayerCount(), ru);
    }
}
