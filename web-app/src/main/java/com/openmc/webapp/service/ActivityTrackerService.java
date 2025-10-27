package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Service for fetching data from the Activity Tracker plugin API
 */
@Service
public class ActivityTrackerService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityTrackerService.class);
    
    private final ServerConfig serverConfig;
    private final RestTemplate restTemplate;
    
    public ActivityTrackerService(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.restTemplate = new RestTemplate();
        logConfiguration();
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
        
        try {
            String url = buildUrl("/api/stats");
            logger.debug("Fetching Activity Tracker stats from: {}", url);
            ActivityTrackerStats stats = restTemplate.getForObject(url, ActivityTrackerStats.class);
            if (stats != null) {
                logger.info("Successfully fetched Activity Tracker stats: {} unique logins, {} total logins", 
                    stats.getUniqueLogins(), stats.getTotalLogins());
            } else {
                logger.warn("Activity Tracker stats response was null");
            }
            return stats;
        } catch (Exception e) {
            logger.error("Error fetching Activity Tracker stats from {}: {} - {}", 
                buildUrl("/api/stats"), e.getClass().getSimpleName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Stack trace:", e);
            }
            return null;
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
        
        try {
            String url = buildUrl("/api/leaderboard");
            logger.debug("Fetching Activity Tracker leaderboard from: {}", url);
            LeaderboardEntry[] entries = restTemplate.getForObject(url, LeaderboardEntry[].class);
            List<LeaderboardEntry> leaderboard = entries != null ? Arrays.asList(entries) : Collections.emptyList();
            logger.info("Successfully fetched Activity Tracker leaderboard with {} entries", leaderboard.size());
            return leaderboard;
        } catch (Exception e) {
            logger.error("Error fetching Activity Tracker leaderboard from {}: {} - {}", 
                buildUrl("/api/leaderboard"), e.getClass().getSimpleName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Stack trace:", e);
            }
            return Collections.emptyList();
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
}
