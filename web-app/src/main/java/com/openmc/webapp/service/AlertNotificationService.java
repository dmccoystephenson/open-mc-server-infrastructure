package com.openmc.webapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending alerts to the alert manager
 */
@Service
public class AlertNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertNotificationService.class);
    
    @Value("${alert.manager.url:http://alert-manager:8090}")
    private String alertManagerUrl;
    
    @Value("${alert.manager.enabled:true}")
    private boolean alertsEnabled;
    
    private final RestTemplate restTemplate;
    
    public AlertNotificationService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Sends an alert to the alert manager
     * 
     * @param title Alert title
     * @param message Alert message
     * @param level Alert level (INFO, WARNING, ERROR, CRITICAL)
     */
    public void sendAlert(String title, String message, String level) {
        if (!alertsEnabled) {
            logger.debug("Alerts are disabled, skipping alert: {}", title);
            return;
        }
        
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("title", title);
            alert.put("message", message);
            alert.put("level", level);
            alert.put("source", "webapp");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(alert, headers);
            
            String url = alertManagerUrl + "/api/alerts";
            restTemplate.postForObject(url, request, String.class);
            
            logger.info("Alert sent successfully: {}", title);
        } catch (Exception e) {
            logger.error("Failed to send alert to alert manager: {}", title, e);
        }
    }
    
    /**
     * Sends an INFO level alert
     * 
     * @param title Alert title
     * @param message Alert message
     */
    public void sendInfoAlert(String title, String message) {
        sendAlert(title, message, "INFO");
    }
    
    /**
     * Sends a WARNING level alert
     * 
     * @param title Alert title
     * @param message Alert message
     */
    public void sendWarningAlert(String title, String message) {
        sendAlert(title, message, "WARNING");
    }
    
    /**
     * Sends an ERROR level alert
     * 
     * @param title Alert title
     * @param message Alert message
     */
    public void sendErrorAlert(String title, String message) {
        sendAlert(title, message, "ERROR");
    }
}
