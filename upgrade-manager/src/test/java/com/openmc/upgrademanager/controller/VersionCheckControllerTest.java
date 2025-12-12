package com.openmc.upgrademanager.controller;

import com.openmc.upgrademanager.service.MinecraftVersionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VersionCheckController.class)
@DisplayName("VersionCheckController Tests")
class VersionCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MinecraftVersionService versionService;

    @Test
    @DisplayName("Should get current version successfully")
    void shouldGetCurrentVersionSuccessfully() throws Exception {
        when(versionService.getCurrentVersion()).thenReturn("1.21.10");
        
        mockMvc.perform(get("/api/version/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.21.10"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should get latest version successfully")
    void shouldGetLatestVersionSuccessfully() throws Exception {
        when(versionService.getLatestMinecraftVersion()).thenReturn("1.21.11");
        
        mockMvc.perform(get("/api/version/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.21.11"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should check version and detect outdated server")
    void shouldCheckVersionAndDetectOutdatedServer() throws Exception {
        when(versionService.getCurrentVersion()).thenReturn("1.21.10");
        when(versionService.getLatestMinecraftVersion()).thenReturn("1.21.11");
        
        mockMvc.perform(get("/api/version/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.21.10"))
                .andExpect(jsonPath("$.latestVersion").value("1.21.11"))
                .andExpect(jsonPath("$.outdated").value(true))
                .andExpect(jsonPath("$.message").value("Server is outdated"));
    }

    @Test
    @DisplayName("Should check version and detect up-to-date server")
    void shouldCheckVersionAndDetectUpToDateServer() throws Exception {
        when(versionService.getCurrentVersion()).thenReturn("1.21.11");
        when(versionService.getLatestMinecraftVersion()).thenReturn("1.21.11");
        
        mockMvc.perform(get("/api/version/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.21.11"))
                .andExpect(jsonPath("$.latestVersion").value("1.21.11"))
                .andExpect(jsonPath("$.outdated").value(false))
                .andExpect(jsonPath("$.message").value("Server is up to date"));
    }
}
