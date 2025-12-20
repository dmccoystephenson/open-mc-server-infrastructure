package com.openmc.webapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertNotificationService Tests")
class AlertNotificationServiceTest {
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private RestTemplateBuilder restTemplateBuilder;
    
    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;
    
    private AlertNotificationService alertService;
    
    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        
        alertService = new AlertNotificationService(restTemplateBuilder);
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://test-alert-manager:8090");
        ReflectionTestUtils.setField(alertService, "alertsEnabled", true);
    }
    
    @Test
    @DisplayName("Should send alert when enabled")
    void shouldSendAlertWhenEnabled() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        alertService.sendAlert("Test Title", "Test Message", "INFO");
        
        verify(restTemplate, times(1)).postForObject(
                eq("http://test-alert-manager:8090/api/alerts"),
                requestCaptor.capture(),
                eq(String.class)
        );
        
        Map<String, Object> alertBody = requestCaptor.getValue().getBody();
        assertNotNull(alertBody);
        assertEquals("Test Title", alertBody.get("title"));
        assertEquals("Test Message", alertBody.get("message"));
        assertEquals("INFO", alertBody.get("level"));
        assertEquals("webapp", alertBody.get("source"));
    }
    
    @Test
    @DisplayName("Should not send alert when disabled")
    void shouldNotSendAlertWhenDisabled() {
        ReflectionTestUtils.setField(alertService, "alertsEnabled", false);
        
        alertService.sendAlert("Test Title", "Test Message", "INFO");
        
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }
    
    @Test
    @DisplayName("Should handle null title gracefully")
    void shouldHandleNullTitle() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        assertDoesNotThrow(() -> alertService.sendAlert(null, "Test Message", "INFO"));
        
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
    }
    
    @Test
    @DisplayName("Should handle null message gracefully")
    void shouldHandleNullMessage() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        assertDoesNotThrow(() -> alertService.sendAlert("Test Title", null, "INFO"));
        
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
    }
    
    @Test
    @DisplayName("Should handle alert manager unavailable")
    void shouldHandleAlertManagerUnavailable() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));
        
        // Should not throw exception - just log error
        assertDoesNotThrow(() -> alertService.sendAlert("Test Title", "Test Message", "INFO"));
        
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
    }
    
    @Test
    @DisplayName("Should send INFO alert")
    void shouldSendInfoAlert() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        alertService.sendInfoAlert("Test Title", "Test Message");
        
        verify(restTemplate, times(1)).postForObject(
                anyString(),
                requestCaptor.capture(),
                eq(String.class)
        );
        
        Map<String, Object> alertBody = requestCaptor.getValue().getBody();
        assertEquals("INFO", alertBody.get("level"));
    }
    
    @Test
    @DisplayName("Should send WARNING alert")
    void shouldSendWarningAlert() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        alertService.sendWarningAlert("Test Title", "Test Message");
        
        verify(restTemplate, times(1)).postForObject(
                anyString(),
                requestCaptor.capture(),
                eq(String.class)
        );
        
        Map<String, Object> alertBody = requestCaptor.getValue().getBody();
        assertEquals("WARNING", alertBody.get("level"));
    }
    
    @Test
    @DisplayName("Should send ERROR alert")
    void shouldSendErrorAlert() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        alertService.sendErrorAlert("Test Title", "Test Message");
        
        verify(restTemplate, times(1)).postForObject(
                anyString(),
                requestCaptor.capture(),
                eq(String.class)
        );
        
        Map<String, Object> alertBody = requestCaptor.getValue().getBody();
        assertEquals("ERROR", alertBody.get("level"));
    }
    
    @Test
    @DisplayName("Should construct correct URL")
    void shouldConstructCorrectUrl() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("Success");
        
        alertService.sendAlert("Test Title", "Test Message", "INFO");
        
        verify(restTemplate).postForObject(
                eq("http://test-alert-manager:8090/api/alerts"),
                any(),
                eq(String.class)
        );
    }
}
