package com.openmc.alertmanager.controller;

import com.openmc.alertmanager.model.AlertLevel;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.service.AlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@DisplayName("AlertController Tests")
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertService alertService;

    @Test
    @DisplayName("Should return recent alerts with default limit")
    void shouldReturnRecentAlertsWithDefaultLimit() throws Exception {
        AlertRecord record = AlertRecord.builder()
                .title("Server Started")
                .message("Minecraft server started successfully")
                .level(AlertLevel.INFO)
                .source("minecraft-wrapper")
                .receivedAt(Instant.parse("2024-01-01T02:00:00Z"))
                .build();

        when(alertService.getRecentAlerts(10)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].title").value("Server Started"))
                .andExpect(jsonPath("$[0].source").value("minecraft-wrapper"))
                .andExpect(jsonPath("$[0].level").value("INFO"));

        verify(alertService).getRecentAlerts(10);
    }

    @Test
    @DisplayName("Should return recent alerts with custom limit")
    void shouldReturnRecentAlertsWithCustomLimit() throws Exception {
        when(alertService.getRecentAlerts(5)).thenReturn(List.of());

        mockMvc.perform(get("/api/alerts?limit=5")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());

        verify(alertService).getRecentAlerts(5);
    }

    @Test
    @DisplayName("Should return empty list when no alerts stored")
    void shouldReturnEmptyListWhenNoAlertsStored() throws Exception {
        when(alertService.getRecentAlerts(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Should send alert successfully")
    void shouldSendAlertSuccessfully() throws Exception {
        mockMvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Alert\",\"message\":\"Test message\",\"level\":\"INFO\",\"source\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Alert sent successfully"));

        verify(alertService).sendAlert(any());
    }

    @Test
    @DisplayName("Should return 400 when alert title is blank")
    void shouldReturn400WhenAlertTitleIsBlank() throws Exception {
        mockMvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"message\":\"Test message\",\"level\":\"INFO\",\"source\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when alert message is blank")
    void shouldReturn400WhenAlertMessageIsBlank() throws Exception {
        mockMvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test\",\"message\":\"\",\"level\":\"INFO\",\"source\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when alert level is null")
    void shouldReturn400WhenAlertLevelIsNull() throws Exception {
        mockMvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test\",\"message\":\"Test message\",\"source\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return health status")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/alerts/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Alert Manager is running"));
    }

    @Test
    @DisplayName("Should return 500 when alert service throws exception")
    void shouldReturn500WhenAlertServiceThrowsException() throws Exception {
        doThrow(new RuntimeException("Service failure")).when(alertService).sendAlert(any());

        mockMvc.perform(post("/api/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test\",\"message\":\"Test message\",\"level\":\"INFO\",\"source\":\"test\"}"))
                .andExpect(status().isInternalServerError());
    }
}
