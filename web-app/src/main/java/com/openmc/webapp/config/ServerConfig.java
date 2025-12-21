package com.openmc.webapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minecraft.server")
public class ServerConfig {
    
    private String host = "mcserver";
    private int rconPort = 25575;
    private String rconPassword = "minecraft";
    private String motd = "A Private Minecraft Server";
    private int maxPlayers = 20;
    private String dynmapUrl = "";
    private String bluemapUrl = "";
    private String adminUsername = "admin";
    private String adminPassword = "admin";
    private long refreshIntervalMs = 1800000; // Default: 30 minutes
    private String activityTrackerUrl = "";
    private boolean activityTrackerEnabled = false;
    private String dashboardTitle = "Minecraft Server Dashboard";
    private String dashboardSubtitle = "Server Management & Information";
    private String dashboardPrimaryColor = "#667eea";
    private String dashboardSecondaryColor = "#764ba2";
    private String pluginsDirectory = "/mcserver/plugins";
    
    // Getters and setters
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getRconPort() {
        return rconPort;
    }
    
    public void setRconPort(int rconPort) {
        this.rconPort = rconPort;
    }
    
    public String getRconPassword() {
        return rconPassword;
    }
    
    public void setRconPassword(String rconPassword) {
        this.rconPassword = rconPassword;
    }
    
    public String getMotd() {
        return motd;
    }
    
    public void setMotd(String motd) {
        this.motd = motd;
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }
    
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
    
    public String getDynmapUrl() {
        return dynmapUrl;
    }
    
    public void setDynmapUrl(String dynmapUrl) {
        this.dynmapUrl = dynmapUrl;
    }
    
    public String getBluemapUrl() {
        return bluemapUrl;
    }
    
    public void setBluemapUrl(String bluemapUrl) {
        this.bluemapUrl = bluemapUrl;
    }
    
    public String getAdminUsername() {
        return adminUsername;
    }
    
    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }
    
    public String getAdminPassword() {
        return adminPassword;
    }
    
    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
    
    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }
    
    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = refreshIntervalMs;
    }
    
    public String getActivityTrackerUrl() {
        return activityTrackerUrl;
    }
    
    public void setActivityTrackerUrl(String activityTrackerUrl) {
        this.activityTrackerUrl = activityTrackerUrl;
    }
    
    public boolean isActivityTrackerEnabled() {
        return activityTrackerEnabled;
    }
    
    public void setActivityTrackerEnabled(boolean activityTrackerEnabled) {
        this.activityTrackerEnabled = activityTrackerEnabled;
    }
    
    public String getDashboardTitle() {
        return dashboardTitle;
    }
    
    public void setDashboardTitle(String dashboardTitle) {
        this.dashboardTitle = dashboardTitle;
    }
    
    public String getDashboardSubtitle() {
        return dashboardSubtitle;
    }
    
    public void setDashboardSubtitle(String dashboardSubtitle) {
        this.dashboardSubtitle = dashboardSubtitle;
    }
    
    public String getDashboardPrimaryColor() {
        return dashboardPrimaryColor;
    }
    
    public void setDashboardPrimaryColor(String dashboardPrimaryColor) {
        this.dashboardPrimaryColor = dashboardPrimaryColor;
    }
    
    public String getDashboardSecondaryColor() {
        return dashboardSecondaryColor;
    }
    
    public void setDashboardSecondaryColor(String dashboardSecondaryColor) {
        this.dashboardSecondaryColor = dashboardSecondaryColor;
    }
    
    public String getPluginsDirectory() {
        return pluginsDirectory;
    }
    
    public void setPluginsDirectory(String pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
    }
}
