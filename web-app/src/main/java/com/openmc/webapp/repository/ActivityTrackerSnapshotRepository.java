package com.openmc.webapp.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmc.webapp.entity.ActivityTrackerSnapshotEntity;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.repository.jpa.ActivityTrackerSnapshotJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class ActivityTrackerSnapshotRepository implements Repository<ActivityTrackerSnapshot> {

    private static final Logger logger = LoggerFactory.getLogger(ActivityTrackerSnapshotRepository.class);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final ActivityTrackerSnapshotJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public ActivityTrackerSnapshotRepository(ActivityTrackerSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    @Transactional
    public void save(List<ActivityTrackerSnapshot> entities) {
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
    public List<ActivityTrackerSnapshot> findAll() {
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

    private ActivityTrackerSnapshotEntity toEntity(ActivityTrackerSnapshot m) {
        Integer uniqueLogins = m.getStats() != null ? m.getStats().getUniqueLogins() : null;
        Integer totalLogins = m.getStats() != null ? m.getStats().getTotalLogins() : null;
        String leaderboardJson = null;
        if (m.getLeaderboard() != null) {
            try {
                leaderboardJson = objectMapper.writeValueAsString(m.getLeaderboard());
            } catch (Exception e) {
                logger.warn("Failed to serialize leaderboard: {}", e.getMessage());
            }
        }
        return new ActivityTrackerSnapshotEntity(m.getTimestamp(), m.isSuccess(),
                uniqueLogins, totalLogins, leaderboardJson);
    }

    private ActivityTrackerSnapshot toModel(ActivityTrackerSnapshotEntity e) {
        ActivityTrackerStats stats = null;
        if (e.getUniqueLogins() != null && e.getTotalLogins() != null) {
            stats = new ActivityTrackerStats(e.getUniqueLogins(), e.getTotalLogins());
        }
        List<LeaderboardEntry> leaderboard = Collections.emptyList();
        if (e.getLeaderboard() != null) {
            try {
                leaderboard = objectMapper.readValue(e.getLeaderboard(),
                        new TypeReference<List<LeaderboardEntry>>() {});
            } catch (Exception ex) {
                logger.warn("Failed to deserialize leaderboard: {}", ex.getMessage());
            }
        }
        return new ActivityTrackerSnapshot(e.getTimestamp(), stats, leaderboard, e.isSuccess());
    }
}
