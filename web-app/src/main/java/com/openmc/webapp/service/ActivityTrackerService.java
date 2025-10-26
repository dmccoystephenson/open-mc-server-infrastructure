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
    }
    
    /**
     * Check if Activity Tracker integration is enabled and configured
     */
    public boolean isEnabled() {
        return serverConfig.isActivityTrackerEnabled() 
            && serverConfig.getActivityTrackerUrl() != null 
            && !serverConfig.getActivityTrackerUrl().trim().isEmpty();
    }
    
    /**
     * Fetch server statistics from Activity Tracker
     */
    public ActivityTrackerStats getStats() {
        if (!isEnabled()) {
            return null;
        }
        
        try {
            String url = buildUrl("/api/stats");
            return restTemplate.getForObject(url, ActivityTrackerStats.class);
        } catch (Exception e) {
            logger.error("Error fetching Activity Tracker stats: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Fetch leaderboard from Activity Tracker
     */
    public List<LeaderboardEntry> getLeaderboard() {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        
        try {
            String url = buildUrl("/api/leaderboard");
            LeaderboardEntry[] entries = restTemplate.getForObject(url, LeaderboardEntry[].class);
            return entries != null ? Arrays.asList(entries) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error fetching Activity Tracker leaderboard: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Check if Activity Tracker API is healthy
     */
    public boolean isHealthy() {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            String url = buildUrl("/api/health");
            restTemplate.getForObject(url, String.class);
            return true;
        } catch (Exception e) {
            logger.debug("Activity Tracker health check failed: {}", e.getMessage());
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
