package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.model.Alert;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Collections;

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertService, "alertManagerUrl", "http://test:8090/api/alerts");
        ReflectionTestUtils.setField(alertService, "alertsServerStart", true);
        ReflectionTestUtils.setField(alertService, "alertsServerStop", true);
        ReflectionTestUtils.setField(alertService, "alertsServerCrash", true);
    }

    @Test
    @DisplayName("Should send alert with correct parameters")
    void shouldSendAlertWithCorrectParameters() {
        alertService.sendAlert("Test Title", "Test Message", "INFO", null);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://test:8090/api/alerts"), captor.capture(), eq(String.class));

        HttpEntity<Alert> entity = captor.getValue();
        Alert alert = entity.getBody();
        
        assertNotNull(alert);
        assertEquals("Test Title", alert.getTitle());
        assertEquals("Test Message", alert.getMessage());
        assertEquals("INFO", alert.getLevel());
        assertEquals("minecraft-server", alert.getSource());
        assertEquals(Collections.singletonList("DISCORD"), alert.getDestinations());
    }

    @Test
    @DisplayName("Should skip alert when toggle is disabled")
    void shouldSkipAlertWhenToggleDisabled() {
        ReflectionTestUtils.setField(alertService, "alertsServerStart", false);

        alertService.sendAlert("Test", "Message", "INFO", "ALERTS_SERVER_START");

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should send alert when toggle is enabled")
    void shouldSendAlertWhenToggleEnabled() {
        ReflectionTestUtils.setField(alertService, "alertsServerStart", true);

        alertService.sendAlert("Test", "Message", "INFO", "ALERTS_SERVER_START");

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should handle RestTemplate exception gracefully")
    void shouldHandleRestTemplateExceptionGracefully() {
        when(restTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Connection error"));

        assertDoesNotThrow(() -> alertService.sendAlert("Test", "Message", "INFO", null));
    }

    @Test
    @DisplayName("Should send server start alert")
    void shouldSendServerStartAlert() {
        alertService.sendServerStartAlert();

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = (Alert) captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals("Minecraft Server Started", alert.getTitle());
        assertEquals("INFO", alert.getLevel());
    }

    @Test
    @DisplayName("Should send server stop alert")
    void shouldSendServerStopAlert() {
        alertService.sendServerStopAlert();

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = (Alert) captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals("Minecraft Server Stopped", alert.getTitle());
        assertEquals("INFO", alert.getLevel());
    }

    @Test
    @DisplayName("Should send server crash alert with exit code")
    void shouldSendServerCrashAlertWithExitCode() {
        alertService.sendServerCrashAlert(1);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = (Alert) captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals("Minecraft Server Crashed", alert.getTitle());
        assertTrue(alert.getMessage().contains("code 1"));
        assertEquals("ERROR", alert.getLevel());
    }

    @Test
    @DisplayName("Should send alert with custom destinations")
    void shouldSendAlertWithCustomDestinations() {
        alertService.sendAlert("Test", "Message", "INFO", null, 
                Collections.singletonList("MINECRAFT"));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = (Alert) captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals(Collections.singletonList("MINECRAFT"), alert.getDestinations());
    }
}
