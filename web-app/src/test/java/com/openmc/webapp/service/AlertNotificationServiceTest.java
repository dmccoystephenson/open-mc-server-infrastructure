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
import org.springframework.http.ResponseEntity;
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
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://test-alert-manager:8090/api/alerts");
        ReflectionTestUtils.setField(alertService, "alertsEnabled", true);
    }

    @Test
    @DisplayName("Should send alert when enabled")
    void shouldSendAlertWhenEnabled() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        alertService.sendAlert("Test Title", "Test Message", "INFO");

        verify(restTemplate, times(1)).postForEntity(
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

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should handle null title gracefully")
    void shouldHandleNullTitle() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        assertDoesNotThrow(() -> alertService.sendAlert(null, "Test Message", "INFO"));

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should handle null message gracefully")
    void shouldHandleNullMessage() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        assertDoesNotThrow(() -> alertService.sendAlert("Test Title", null, "INFO"));

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should handle alert manager unavailable")
    void shouldHandleAlertManagerUnavailable() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertDoesNotThrow(() -> alertService.sendAlert("Test Title", "Test Message", "INFO"));

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should log warning when alert manager returns non-2xx")
    void shouldLogWarningOnNon2xxResponse() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.internalServerError().build());

        assertDoesNotThrow(() -> alertService.sendAlert("Test Title", "Test Message", "INFO"));

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should send INFO alert")
    void shouldSendInfoAlert() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        alertService.sendInfoAlert("Test Title", "Test Message");

        verify(restTemplate, times(1)).postForEntity(
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
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        alertService.sendWarningAlert("Test Title", "Test Message");

        verify(restTemplate, times(1)).postForEntity(
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
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        alertService.sendErrorAlert("Test Title", "Test Message");

        verify(restTemplate, times(1)).postForEntity(
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
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        alertService.sendAlert("Test Title", "Test Message", "INFO");

        verify(restTemplate).postForEntity(
                eq("http://test-alert-manager:8090/api/alerts"),
                any(),
                eq(String.class)
        );
    }

    // ALERT_MANAGER_URL is one .env value shared with minecraft-wrapper, backup-manager and
    // agent-manager, all of which POST to it verbatim. Appending a path here would send
    // dashboard alerts to /api/alerts/api/alerts and 404.
    @Test
    @DisplayName("Should post to the configured URL verbatim without appending a path")
    void shouldPostToConfiguredUrlVerbatim() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://custom-host:9000/custom/alerts");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        alertService.sendAlert("Test Title", "Test Message", "INFO");

        verify(restTemplate).postForEntity(
                eq("http://custom-host:9000/custom/alerts"),
                any(),
                eq(String.class)
        );
    }
}
