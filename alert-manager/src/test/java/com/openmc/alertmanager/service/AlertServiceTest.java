package com.openmc.alertmanager.service;

import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertDestination;
import com.openmc.alertmanager.model.AlertLevel;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService Tests")
class AlertServiceTest {

    @Mock
    private DiscordAlertService discordAlertService;

    @Mock
    private MinecraftMessageService minecraftMessageService;

    @Mock
    private AlertRepository alertRepository;

    private AlertService alertService;

    private Alert testAlert;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(discordAlertService, minecraftMessageService, alertRepository);
        testAlert = Alert.builder()
            .title("Test Alert")
            .message("This is a test alert message")
            .level(AlertLevel.INFO)
            .source("test-module")
            .build();
    }

    @Test
    @DisplayName("Should send alert to all destinations when none specified")
    void shouldSendAlertToAllDestinationsWhenNoneSpecified() throws Exception {
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
        verify(alertRepository, times(1)).save(any(AlertRecord.class));
    }

    @Test
    @DisplayName("Should send alert to Discord only when specified")
    void shouldSendAlertToDiscordOnlyWhenSpecified() throws Exception {
        testAlert.setDestinations(Collections.singletonList(AlertDestination.DISCORD));
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should send alert to Minecraft only when specified")
    void shouldSendAlertToMinecraftOnlyWhenSpecified() throws Exception {
        testAlert.setDestinations(Collections.singletonList(AlertDestination.MINECRAFT));
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, never()).sendAlert(any());
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should not throw exception when Discord service fails")
    void shouldNotThrowExceptionWhenDiscordServiceFails() throws Exception {
        doThrow(new RuntimeException("Discord error")).when(discordAlertService).sendAlert(any());
        
        assertDoesNotThrow(() -> alertService.sendAlert(testAlert));
    }

    @Test
    @DisplayName("Should not throw exception when Minecraft service fails")
    void shouldNotThrowExceptionWhenMinecraftServiceFails() throws Exception {
        doThrow(new RuntimeException("Minecraft error")).when(minecraftMessageService).sendMessage(anyString());
        
        assertDoesNotThrow(() -> alertService.sendAlert(testAlert));
    }

    @Test
    @DisplayName("Should process alerts with different levels")
    void shouldProcessAlertsWithDifferentLevels() throws Exception {
        for (AlertLevel level : AlertLevel.values()) {
            Alert alert = Alert.builder()
                .title("Test " + level)
                .message("Test message")
                .level(level)
                .source("test")
                .build();
            
            alertService.sendAlert(alert);
        }
        
        verify(discordAlertService, times(AlertLevel.values().length)).sendAlert(any());
        verify(minecraftMessageService, times(AlertLevel.values().length)).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should return recent alerts from repository")
    void shouldReturnRecentAlertsFromRepository() {
        List<AlertRecord> expected = List.of(
            AlertRecord.builder().title("Test").level(AlertLevel.INFO).receivedAt(java.time.Instant.now()).build()
        );
        when(alertRepository.findAllByOrderByReceivedAtDesc(any(PageRequest.class))).thenReturn(expected);

        List<AlertRecord> result = alertService.getRecentAlerts(10);
        assertEquals(1, result.size());
        assertEquals("Test", result.get(0).getTitle());
    }
}
