package com.openmc.upgrademanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmc.upgrademanager.exception.UpgradeException;
import com.openmc.upgrademanager.model.UpgradeRequest;
import com.openmc.upgrademanager.service.UpgradeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpgradeController.class)
@DisplayName("UpgradeController Tests")
class UpgradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UpgradeService upgradeService;

    @Test
    @DisplayName("Should trigger upgrade successfully")
    void shouldTriggerUpgradeSuccessfully() throws Exception {
        // Set up mock service response
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("success", true);
        serviceResult.put("message", "Successfully upgraded from 1.20.0 to 1.21.0");
        serviceResult.put("previousVersion", "1.20.0");
        serviceResult.put("newVersion", "1.21.0");
        serviceResult.put("backupPath", "/backups/backup-20240101-120000");
        
        when(upgradeService.performUpgrade("1.21.0")).thenReturn(serviceResult);

        // Create request
        UpgradeRequest request = new UpgradeRequest("1.21.0");

        // Perform request and verify response
        mockMvc.perform(post("/api/upgrades/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully upgraded from 1.20.0 to 1.21.0"))
                .andExpect(jsonPath("$.previousVersion").value("1.20.0"))
                .andExpect(jsonPath("$.newVersion").value("1.21.0"))
                .andExpect(jsonPath("$.backupPath").value("/backups/backup-20240101-120000"));
    }

    @Test
    @DisplayName("Should return error when version is missing")
    void shouldReturnErrorWhenVersionIsMissing() throws Exception {
        // Create request with null version
        UpgradeRequest request = new UpgradeRequest(null);

        // Perform request and verify response
        mockMvc.perform(post("/api/upgrades/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New version is required"))
                .andExpect(jsonPath("$.error").value("Missing or empty version parameter"));
    }

    @Test
    @DisplayName("Should return error when version is empty")
    void shouldReturnErrorWhenVersionIsEmpty() throws Exception {
        // Create request with empty version
        UpgradeRequest request = new UpgradeRequest("   ");

        // Perform request and verify response
        mockMvc.perform(post("/api/upgrades/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("New version is required"));
    }

    @Test
    @DisplayName("Should return error when upgrade service throws exception")
    void shouldReturnErrorWhenUpgradeServiceThrowsException() throws Exception {
        // Set up mock to throw exception
        when(upgradeService.performUpgrade(anyString()))
                .thenThrow(new UpgradeException("Docker build failed"));

        // Create request
        UpgradeRequest request = new UpgradeRequest("1.21.0");

        // Perform request and verify response
        mockMvc.perform(post("/api/upgrades/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Upgrade failed"))
                .andExpect(jsonPath("$.error").value("Docker build failed"));
    }
}
