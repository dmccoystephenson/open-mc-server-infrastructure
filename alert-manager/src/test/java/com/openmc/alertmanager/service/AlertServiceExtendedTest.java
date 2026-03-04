package com.openmc.alertmanager.service;

import com.openmc.alertmanager.exception.AlertException;
import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertDestination;
import com.openmc.alertmanager.model.AlertLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("AlertService Extended Tests")
class AlertServiceExtendedTest {

    @Autowired
    private AlertService alertService;

    @MockBean
    private DiscordAlertService discordAlertService;

    @MockBean
    private MinecraftMessageService minecraftMessageService;

    @MockBean
    private RateLimitService rateLimitService;

    private Alert testAlert;

    @BeforeEach
    void setUp() {
        testAlert = Alert.builder()
            .title("Test Alert")
            .message("This is a test alert message")
            .level(AlertLevel.INFO)
            .source("test-module")
            .build();
        
        // By default, allow all alerts (no rate limiting)
        when(rateLimitService.shouldAllowAlert(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Should handle alert with both destinations specified")
    void shouldHandleAlertWithBothDestinationsSpecified() throws Exception {
        testAlert.setDestinations(Arrays.asList(AlertDestination.DISCORD, AlertDestination.MINECRAFT));
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
        verify(rateLimitService, times(1)).shouldAllowAlert("DISCORD");
        verify(rateLimitService, times(1)).shouldAllowAlert("MINECRAFT");
    }

    @Test
    @DisplayName("Should continue sending to other destinations when one is rate limited")
    void shouldContinueSendingToOtherDestinationsWhenOneIsRateLimited() throws Exception {
        // Discord is rate limited, but Minecraft is not
        when(rateLimitService.shouldAllowAlert("DISCORD")).thenReturn(false);
        when(rateLimitService.shouldAllowAlert("MINECRAFT")).thenReturn(true);
        
        testAlert.setDestinations(Arrays.asList(AlertDestination.DISCORD, AlertDestination.MINECRAFT));
        alertService.sendAlert(testAlert);
        
        // Discord should be skipped
        verify(discordAlertService, never()).sendAlert(any());
        // Minecraft should still be sent
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should continue processing when Discord service throws exception")
    void shouldContinueProcessingWhenDiscordServiceThrowsException() throws Exception {
        doThrow(new AlertException("Discord failed")).when(discordAlertService).sendAlert(any());
        
        alertService.sendAlert(testAlert);
        
        // Discord threw exception but Minecraft should still be called
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should continue processing when Minecraft service throws exception")
    void shouldContinueProcessingWhenMinecraftServiceThrowsException() throws Exception {
        doThrow(new RuntimeException("Minecraft failed")).when(minecraftMessageService).sendMessage(anyString());
        
        alertService.sendAlert(testAlert);
        
        // Discord should still be called even though Minecraft threw exception
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should handle alert with empty destinations list defaulting to all")
    void shouldHandleAlertWithEmptyDestinationsListDefaultingToAll() throws Exception {
        testAlert.setDestinations(Collections.emptyList());
        alertService.sendAlert(testAlert);
        
        // Should send to all destinations when list is empty
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should handle all alert levels with rate limiting")
    void shouldHandleAllAlertLevelsWithRateLimiting() throws Exception {
        // Rate limit Discord for all levels
        when(rateLimitService.shouldAllowAlert("DISCORD")).thenReturn(false);
        when(rateLimitService.shouldAllowAlert("MINECRAFT")).thenReturn(true);
        
        for (AlertLevel level : AlertLevel.values()) {
            Alert alert = Alert.builder()
                .title("Test " + level)
                .message("Test message for " + level)
                .level(level)
                .source("test")
                .build();
            
            alertService.sendAlert(alert);
        }
        
        // Discord should never be called due to rate limiting
        verify(discordAlertService, never()).sendAlert(any());
        // Minecraft should be called for each level
        verify(minecraftMessageService, times(AlertLevel.values().length)).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should check rate limit before calling service")
    void shouldCheckRateLimitBeforeCallingService() throws Exception {
        // Make Discord rate limited
        when(rateLimitService.shouldAllowAlert("DISCORD")).thenReturn(false);
        
        testAlert.setDestinations(Collections.singletonList(AlertDestination.DISCORD));
        alertService.sendAlert(testAlert);
        
        // Should check rate limit
        verify(rateLimitService, times(1)).shouldAllowAlert("DISCORD");
        // Should not call Discord service because rate limited
        verify(discordAlertService, never()).sendAlert(any());
    }

    @Test
    @DisplayName("Should handle alert with null title")
    void shouldHandleAlertWithNullTitle() throws Exception {
        testAlert.setTitle(null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> alertService.sendAlert(testAlert));
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should handle alert with null source")
    void shouldHandleAlertWithNullSource() throws Exception {
        testAlert.setSource(null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> alertService.sendAlert(testAlert));
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should pass correct message to Minecraft service")
    void shouldPassCorrectMessageToMinecraftService() throws Exception {
        String expectedMessage = "Custom test message";
        testAlert.setMessage(expectedMessage);
        testAlert.setDestinations(Collections.singletonList(AlertDestination.MINECRAFT));
        
        alertService.sendAlert(testAlert);
        
        verify(minecraftMessageService, times(1)).sendMessage(expectedMessage);
    }

    @Test
    @DisplayName("Should handle multiple alerts in sequence with mixed rate limiting")
    void shouldHandleMultipleAlertsInSequenceWithMixedRateLimiting() throws Exception {
        // First alert: both allowed
        when(rateLimitService.shouldAllowAlert(anyString())).thenReturn(true);
        alertService.sendAlert(testAlert);
        
        // Second alert: Discord rate limited
        when(rateLimitService.shouldAllowAlert("DISCORD")).thenReturn(false);
        when(rateLimitService.shouldAllowAlert("MINECRAFT")).thenReturn(true);
        alertService.sendAlert(testAlert);
        
        // Third alert: both rate limited
        when(rateLimitService.shouldAllowAlert(anyString())).thenReturn(false);
        alertService.sendAlert(testAlert);
        
        // Fourth alert: both allowed again
        when(rateLimitService.shouldAllowAlert(anyString())).thenReturn(true);
        alertService.sendAlert(testAlert);
        
        // Verify call counts: 1 + 0 + 0 + 1 = 2 for Discord
        verify(discordAlertService, times(2)).sendAlert(testAlert);
        // Verify call counts: 1 + 1 + 0 + 1 = 3 for Minecraft
        verify(minecraftMessageService, times(3)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should handle CRITICAL level alerts")
    void shouldHandleCriticalLevelAlerts() throws Exception {
        testAlert.setLevel(AlertLevel.CRITICAL);
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
        verify(rateLimitService, times(2)).shouldAllowAlert(anyString());
    }

    @Test
    @DisplayName("Should handle WARNING level alerts")
    void shouldHandleWarningLevelAlerts() throws Exception {
        testAlert.setLevel(AlertLevel.WARNING);
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should handle ERROR level alerts")
    void shouldHandleErrorLevelAlerts() throws Exception {
        testAlert.setLevel(AlertLevel.ERROR);
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should only send to specified single destination when provided")
    void shouldOnlySendToSpecifiedSingleDestinationWhenProvided() throws Exception {
        testAlert.setDestinations(Collections.singletonList(AlertDestination.DISCORD));
        alertService.sendAlert(testAlert);
        
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, never()).sendMessage(anyString());
        // Should only check rate limit for Discord
        verify(rateLimitService, times(1)).shouldAllowAlert("DISCORD");
        verify(rateLimitService, never()).shouldAllowAlert("MINECRAFT");
    }

    @Test
    @DisplayName("Should handle both services throwing exceptions")
    void shouldHandleBothServicesThrowingExceptions() throws Exception {
        doThrow(new AlertException("Discord failed")).when(discordAlertService).sendAlert(any());
        doThrow(new RuntimeException("Minecraft failed")).when(minecraftMessageService).sendMessage(anyString());
        
        // Should not propagate exceptions
        assertDoesNotThrow(() -> alertService.sendAlert(testAlert));
        
        // Both services should have been attempted
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        verify(minecraftMessageService, times(1)).sendMessage(testAlert.getMessage());
    }

    @Test
    @DisplayName("Should pass correct alert object to Discord service")
    void shouldPassCorrectAlertObjectToDiscordService() throws Exception {
        testAlert.setDestinations(Collections.singletonList(AlertDestination.DISCORD));
        testAlert.setTitle("Specific Title");
        testAlert.setMessage("Specific Message");
        testAlert.setLevel(AlertLevel.CRITICAL);
        testAlert.setSource("specific-source");
        
        alertService.sendAlert(testAlert);
        
        // Verify the exact alert object was passed
        verify(discordAlertService, times(1)).sendAlert(testAlert);
        
        // Verify the alert has the correct properties (would be caught by Discord service)
        assertEquals("Specific Title", testAlert.getTitle());
        assertEquals("Specific Message", testAlert.getMessage());
        assertEquals(AlertLevel.CRITICAL, testAlert.getLevel());
        assertEquals("specific-source", testAlert.getSource());
    }
}
