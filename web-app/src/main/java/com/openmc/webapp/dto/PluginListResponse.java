package com.openmc.webapp.dto;

import java.util.List;

/**
 * Response DTO for plugin list endpoint
 */
public class PluginListResponse {
    private boolean success;
    private List<String> plugins;
    private String error;
    
    public PluginListResponse() {
    }
    
    public PluginListResponse(boolean success, List<String> plugins, String error) {
        this.success = success;
        this.plugins = plugins;
        this.error = error;
    }
    
    public static PluginListResponse success(List<String> plugins) {
        return new PluginListResponse(true, plugins, null);
    }
    
    public static PluginListResponse error(String error) {
        return new PluginListResponse(false, null, error);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public List<String> getPlugins() {
        return plugins;
    }
    
    public void setPlugins(List<String> plugins) {
        this.plugins = plugins;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
}
