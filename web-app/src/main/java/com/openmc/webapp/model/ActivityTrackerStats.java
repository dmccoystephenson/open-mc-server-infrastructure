package com.openmc.webapp.model;

/**
 * Represents server-wide activity statistics from the Activity Tracker plugin
 */
public class ActivityTrackerStats {
    private int uniqueLogins;
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
