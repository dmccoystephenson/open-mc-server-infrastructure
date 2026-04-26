package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.mapper.PlayerProfileMapper;
import com.openmc.webapp.model.ActivityTrackerSnapshot;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.model.PlayerProfile;
import com.openmc.webapp.repository.Repository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Service for fetching data from the Activity Tracker plugin API
 */
@Service
public class ActivityTrackerService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityTrackerService.class);
    private static final int DEFAULT_MAX_HISTORY_SIZE = 10;
    private static final long CACHE_DURATION_MS = 300000; // 5 minutes
    
    private final ServerConfig serverConfig;
    private final RestTemplate restTemplate;
    private final Repository<ActivityTrackerSnapshot> repository;
    private final PlayerProfileMapper playerProfileMapper;
    private final LinkedList<ActivityTrackerSnapshot> snapshotHistory = new LinkedList<>();
    private int maxHistorySize = DEFAULT_MAX_HISTORY_SIZE;
    
    private ActivityTrackerStats cachedStats;
    private List<LeaderboardEntry> cachedLeaderboard;
    private Instant lastFetchTime;
    
    public ActivityTrackerService(ServerConfig serverConfig, Repository<ActivityTrackerSnapshot> repository,
                                  RestTemplate restTemplate, PlayerProfileMapper playerProfileMapper) {
        this.serverConfig = Objects.requireNonNull(serverConfig, "serverConfig must not be null");
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.playerProfileMapper = Objects.requireNonNull(playerProfileMapper, "playerProfileMapper must not be null");
        loadHistoricalData();
        logConfiguration();
    }
    
    private void loadHistoricalData() {
        List<ActivityTrackerSnapshot> loadedSnapshots = repository.findAll();
        if (!loadedSnapshots.isEmpty()) {
            snapshotHistory.addAll(loadedSnapshots);
            while (snapshotHistory.size() > maxHistorySize) {
                snapshotHistory.removeLast();
            }
            
            // Initialize cache with most recent snapshot if available
            if (!snapshotHistory.isEmpty()) {
                ActivityTrackerSnapshot latest = snapshotHistory.getFirst();
                if (latest.isSuccess()) {
                    cachedStats = latest.getStats();
                    cachedLeaderboard = latest.getLeaderboard();
                    lastFetchTime = latest.getTimestamp();
                }
            }
        }
    }
    
    /**
     * Log Activity Tracker configuration on startup
     */
    private void logConfiguration() {
        logger.info("Activity Tracker configuration:");
        logger.info("  - Enabled flag: {}", serverConfig.isActivityTrackerEnabled());
        logger.info("  - URL: {}", serverConfig.getActivityTrackerUrl() != null && !serverConfig.getActivityTrackerUrl().isEmpty() 
            ? serverConfig.getActivityTrackerUrl() : "(not configured)");
        logger.info("  - Integration active: {}", isEnabled());
    }
    
    /**
     * Check if Activity Tracker integration is enabled and configured
     */
    public boolean isEnabled() {
        boolean enabled = serverConfig.isActivityTrackerEnabled();
        String url = serverConfig.getActivityTrackerUrl();
        boolean hasUrl = url != null && !url.trim().isEmpty();
        
        if (enabled && !hasUrl) {
            logger.warn("Activity Tracker is enabled but URL is not configured");
        }
        
        return enabled && hasUrl;
    }
    
    /**
     * Fetch server statistics from Activity Tracker
     */
    public ActivityTrackerStats getStats() {
        if (!isEnabled()) {
            logger.debug("Activity Tracker is not enabled, skipping stats fetch");
            return null;
        }
        
        // Return cached data if still valid
        if (shouldReturnCached()) {
            logger.debug("Returning cached Activity Tracker stats");
            return cachedStats;
        }
        
        // Fetch fresh data
        refreshCache();
        return cachedStats;
    }
    
    private boolean shouldReturnCached() {
        if (lastFetchTime == null || cachedStats == null) {
            return false;
        }
        
        long millisSinceLastFetch = Instant.now().toEpochMilli() - lastFetchTime.toEpochMilli();
        return millisSinceLastFetch < CACHE_DURATION_MS;
    }
    
    private void refreshCache() {
        try {
            String statsUrl = buildUrl("/api/stats");
            String leaderboardUrl = buildUrl("/api/leaderboard");
            
            logger.debug("Fetching Activity Tracker data from: {}", statsUrl);
            ActivityTrackerStats stats = restTemplate.getForObject(statsUrl, ActivityTrackerStats.class);
            LeaderboardEntry[] entries = restTemplate.getForObject(leaderboardUrl, LeaderboardEntry[].class);
            List<LeaderboardEntry> leaderboard = entries != null ? Arrays.asList(entries) : Collections.emptyList();
            
            boolean success = stats != null;
            if (success) {
                logger.info("Successfully fetched Activity Tracker data: {} unique logins, {} total logins, {} leaderboard entries", 
                    stats.getUniqueLogins(), stats.getTotalLogins(), leaderboard.size());
                cachedStats = stats;
                cachedLeaderboard = leaderboard;
            } else {
                logger.warn("Activity Tracker stats response was null");
            }
            
            lastFetchTime = Instant.now();
            
            // Create snapshot and add to history
            ActivityTrackerSnapshot snapshot = new ActivityTrackerSnapshot(
                lastFetchTime, stats, leaderboard, success
            );
            addSnapshot(snapshot);
            
        } catch (Exception e) {
            logger.error("Error fetching Activity Tracker data: {} - {}", 
                e.getClass().getSimpleName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Stack trace:", e);
            }
            
            // Create failed snapshot
            lastFetchTime = Instant.now();
            ActivityTrackerSnapshot snapshot = new ActivityTrackerSnapshot(
                lastFetchTime, null, Collections.emptyList(), false
            );
            addSnapshot(snapshot);
        }
    }
    
    private synchronized void addSnapshot(ActivityTrackerSnapshot snapshot) {
        snapshotHistory.addFirst(snapshot);
        
        // Persist all snapshots to storage (repository handles retention filtering)
        // Load all from repository first to ensure we don't lose older snapshots
        List<ActivityTrackerSnapshot> allSnapshots = new ArrayList<>(repository.findAll());
        // Add new snapshot to the beginning
        allSnapshots.add(0, snapshot);
        repository.save(allSnapshots);
        
        // Keep only the last maxHistorySize snapshots in memory for API responses
        while (snapshotHistory.size() > maxHistorySize) {
            snapshotHistory.removeLast();
        }
    }
    
    /**
     * Fetch leaderboard from Activity Tracker
     */
    public List<LeaderboardEntry> getLeaderboard() {
        if (!isEnabled()) {
            logger.debug("Activity Tracker is not enabled, skipping leaderboard fetch");
            return Collections.emptyList();
        }
        
        // Return cached data if still valid
        if (shouldReturnCached()) {
            logger.debug("Returning cached Activity Tracker leaderboard");
            return cachedLeaderboard != null ? cachedLeaderboard : Collections.emptyList();
        }
        
        // Fetch fresh data (this will also fetch stats)
        refreshCache();
        return cachedLeaderboard != null ? cachedLeaderboard : Collections.emptyList();
    }
    
    /**
     * Get the snapshot history
     */
    public synchronized List<ActivityTrackerSnapshot> getSnapshotHistory() {
        return Collections.unmodifiableList(new ArrayList<>(snapshotHistory));
    }
    
    /**
     * Get the last fetch time
     */
    public Instant getLastFetchTime() {
        return lastFetchTime;
    }
    
    /**
     * Get the current max history size
     * @return Current max history size
     */
    public synchronized int getMaxHistorySize() {
        return maxHistorySize;
    }
    
    /**
     * Set the max history size (session-only, not persisted)
     * @param maxHistorySize New max history size (must be > 0)
     */
    public synchronized void setMaxHistorySize(int maxHistorySize) {
        if (maxHistorySize <= 0) {
            throw new IllegalArgumentException("Max history size must be greater than 0");
        }
        
        int oldSize = this.maxHistorySize;
        this.maxHistorySize = maxHistorySize;
        
        if (maxHistorySize > oldSize && maxHistorySize > snapshotHistory.size()) {
            // If increasing size and we have fewer snapshots than the new limit,
            // reload from repository to get more historical data
            snapshotHistory.clear();
            List<ActivityTrackerSnapshot> allSnapshots = repository.findAll();
            snapshotHistory.addAll(allSnapshots);
        }
        
        // Trim history if it exceeds new size
        while (snapshotHistory.size() > maxHistorySize) {
            snapshotHistory.removeLast();
        }
    }
    
    // Scheduled task to fetch data every 30 minutes regardless of user visits
    @Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    public void scheduledDataFetch() {
        if (isEnabled()) {
            logger.info("Scheduled Activity Tracker data fetch triggered");
            refreshCache();
        }
    }
    
    /**
     * Check if Activity Tracker API is healthy
     */
    public boolean isHealthy() {
        if (!isEnabled()) {
            logger.debug("Activity Tracker is not enabled, health check skipped");
            return false;
        }
        
        try {
            String url = buildUrl("/api/health");
            logger.debug("Performing Activity Tracker health check at: {}", url);
            restTemplate.getForObject(url, String.class);
            logger.info("Activity Tracker health check passed");
            return true;
        } catch (Exception e) {
            logger.warn("Activity Tracker health check failed at {}: {} - {}", 
                buildUrl("/api/health"), e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    private String buildUrl(String path) {
        String baseUrl = serverConfig.getActivityTrackerUrl().trim();
        // Remove trailing slash if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }
    
    /**
     * Get a player profile by player name
     * @param playerName The name of the player
     * @return PlayerProfile with player data, or null if not found
     */
    public PlayerProfile getPlayerProfile(String playerName) {
        if (!isEnabled() || playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        
        List<LeaderboardEntry> leaderboard = getLeaderboard();
        
        // Find the player in the leaderboard
        for (int i = 0; i < leaderboard.size(); i++) {
            LeaderboardEntry entry = leaderboard.get(i);
            if (entry.getPlayerName() != null && entry.getPlayerName().equalsIgnoreCase(playerName)) {
                PlayerProfile profile = playerProfileMapper.toPlayerProfile(entry);
                profile.setLeaderboardRank(i + 1);
                return profile;
            }
        }
        
        return null;
    }
}
