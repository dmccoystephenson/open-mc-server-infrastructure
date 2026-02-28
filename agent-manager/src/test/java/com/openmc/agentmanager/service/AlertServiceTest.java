package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService Tests")
class AlertServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AlertService alertService;

    @Test
    @DisplayName("Should send alert on successful tool execution")
    void shouldSendAlertOnSuccessfulToolExecution() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://alert-manager:8090/api/alerts");

        alertService.sendToolExecutionAlert("dmccoystephenson", "start_server", "please start the server", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Alert>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://alert-manager:8090/api/alerts"), captor.capture(), eq(String.class));

        Alert alert = captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals("Agent Action Executed: Start Server", alert.getTitle());
        assertEquals("INFO", alert.getLevel());
        assertEquals("agent-manager", alert.getSource());
        assertTrue(alert.getMessage().contains("dmccoystephenson"));
        assertTrue(alert.getMessage().contains("Start Server"));
        assertTrue(alert.getMessage().contains("please start the server"));
        assertTrue(alert.getMessage().contains("Success"));
    }

    @Test
    @DisplayName("Should send warning alert on failed tool execution")
    void shouldSendWarningAlertOnFailedToolExecution() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://alert-manager:8090/api/alerts");

        alertService.sendToolExecutionAlert("testuser", "stop_server", "stop the server", false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Alert>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals("Agent Action Failed: Stop Server", alert.getTitle());
        assertEquals("WARNING", alert.getLevel());
        assertTrue(alert.getMessage().contains("testuser"));
        assertTrue(alert.getMessage().contains("Failed"));
    }

    @Test
    @DisplayName("Should skip alert when alert manager URL is not configured")
    void shouldSkipAlertWhenUrlNotConfigured() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "");

        alertService.sendToolExecutionAlert("testuser", "start_server", "start server", true);

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should skip alert when alert manager URL is null")
    void shouldSkipAlertWhenUrlIsNull() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", null);

        alertService.sendToolExecutionAlert("testuser", "start_server", "start server", true);

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should not throw when alert sending fails")
    void shouldNotThrowWhenAlertSendingFails() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://alert-manager:8090/api/alerts");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertDoesNotThrow(() ->
                alertService.sendToolExecutionAlert("testuser", "restart_server", "restart the server", true));
    }

    @Test
    @DisplayName("Should include DISCORD destination in alert")
    void shouldIncludeDiscordDestination() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://alert-manager:8090/api/alerts");

        alertService.sendToolExecutionAlert("testuser", "restart_server", "restart server", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Alert>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = captor.getValue().getBody();
        assertNotNull(alert);
        assertTrue(alert.getDestinations().contains("DISCORD"));
    }
}
