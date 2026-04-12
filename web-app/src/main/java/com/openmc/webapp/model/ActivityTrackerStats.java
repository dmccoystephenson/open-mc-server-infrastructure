package com.openmc.webapp.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents server-wide activity statistics from the Activity Tracker plugin
 */
public class ActivityTrackerStats {
    @Schema(description = "Number of unique player logins")
    private int uniqueLogins;
    @Schema(description = "Total number of logins")
    private int totalLogins;
    
    public ActivityTrackerStats() {
    }
    
    public ActivityTrackerStats(int uniqueLogins, int totalLogins) {
        this.uniqueLogins = uniqueLogins;
        this.totalLogins = totalLogins;
    }
    
    public int getUniqueLogins() {
        return uniqueLogins;
    }
    
    public void setUniqueLogins(int uniqueLogins) {
        this.uniqueLogins = uniqueLogins;
    }
    
    public int getTotalLogins() {
        return totalLogins;
    }
    
    public void setTotalLogins(int totalLogins) {
        this.totalLogins = totalLogins;
    }
}
