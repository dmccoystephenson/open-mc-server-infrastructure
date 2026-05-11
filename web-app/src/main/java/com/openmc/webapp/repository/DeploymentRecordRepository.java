package com.openmc.webapp.repository;

import com.openmc.webapp.entity.DeploymentRecordEntity;
import com.openmc.webapp.model.DeploymentRecord;
import com.openmc.webapp.repository.jpa.DeploymentRecordJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class DeploymentRecordRepository implements Repository<DeploymentRecord> {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final DeploymentRecordJpaRepository jpaRepository;

    public DeploymentRecordRepository(DeploymentRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(List<DeploymentRecord> entities) {
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
    public List<DeploymentRecord> findAll() {
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

    private DeploymentRecordEntity toEntity(DeploymentRecord m) {
        return new DeploymentRecordEntity(m.getTimestamp(), m.getPluginName(), m.getStatus(),
                m.getSource(), m.getBranch(), m.getRepoUrl(), m.getMessage());
    }

    private DeploymentRecord toModel(DeploymentRecordEntity e) {
        return new DeploymentRecord(e.getTimestamp(), e.getPluginName(), e.getStatus(),
                e.getSource(), e.getBranch(), e.getRepoUrl(), e.getMessage());
    }
}
