package com.openmc.upgrademanager.controller;

import com.openmc.upgrademanager.exception.UpgradeException;
import com.openmc.upgrademanager.model.UpgradeResult;
import com.openmc.upgrademanager.service.UpgradeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @MockBean
    private UpgradeService upgradeService;

    @Test
    @DisplayName("Should trigger upgrade successfully")
    void shouldTriggerUpgradeSuccessfully() throws Exception {
        UpgradeResult result = new UpgradeResult(
            true, "Upgrade completed successfully",
            "1.21.10", "1.21.11", "/backups/backup-test"
        );
        
        when(upgradeService.performUpgrade(anyString())).thenReturn(result);
        
        mockMvc.perform(post("/api/upgrade/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"1.21.11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newVersion").value("1.21.11"));
    }

    @Test
    @DisplayName("Should return error when version not provided")
    void shouldReturnErrorWhenVersionNotProvided() throws Exception {
        mockMvc.perform(post("/api/upgrade/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Version parameter is required"));
    }

    @Test
    @DisplayName("Should return error when upgrade fails")
    void shouldReturnErrorWhenUpgradeFails() throws Exception {
        when(upgradeService.performUpgrade(anyString()))
            .thenThrow(new UpgradeException("Upgrade failed"));
        
        mockMvc.perform(post("/api/upgrade/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"1.21.11\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
