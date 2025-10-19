package com.openmc.webapp.controller;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.service.RconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(ServerController.class)
@DisplayName("ServerController Tests")
class ServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RconService rconService;

    @MockBean
    private ServerConfig serverConfig;

    private RconService.ServerStatus mockStatus;

    @BeforeEach
    void setUp() {
        mockStatus = new RconService.ServerStatus(serverConfig, "There are 0 of a max of 20 players online");
        
        when(serverConfig.getMotd()).thenReturn("Test Server");
        when(serverConfig.getMaxPlayers()).thenReturn(20);
        when(serverConfig.getDynmapUrl()).thenReturn("");
        when(serverConfig.getBluemapUrl()).thenReturn("");
        when(serverConfig.getAdminUsername()).thenReturn("admin");
        when(serverConfig.getAdminPassword()).thenReturn("admin");
    }

    @Test
    @DisplayName("Should redirect to /public on GET /")
    void shouldRedirectToPublicOnGetRoot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/public"));
    }

    @Test
    @DisplayName("Should return public page on GET /public")
    void shouldReturnPublicPageOnGetPublic() throws Exception {
        when(rconService.getServerStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/public"))
                .andExpect(status().isOk())
                .andExpect(view().name("public"))
                .andExpect(model().attributeExists("status"))
                .andExpect(model().attributeExists("dynmapUrl"))
                .andExpect(model().attributeExists("bluemapUrl"));
    }

    @Test
    @DisplayName("Should return admin page on GET /admin")
    void shouldReturnAdminPageOnGetAdmin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));
    }

    @Test
    @DisplayName("Should return server status on GET /api/status")
    void shouldReturnServerStatusOnGetApiStatus() throws Exception {
        when(rconService.getServerStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Should accept valid command with authentication")
    void shouldAcceptValidCommandWithAuthentication() throws Exception {
        when(rconService.sendCommand("list")).thenReturn("There are 0 of a max of 20 players online");

        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\",\"command\":\"list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists());
    }

    @Test
    @DisplayName("Should reject command without username")
    void shouldRejectCommandWithoutUsername() throws Exception {
        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin\",\"command\":\"list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(containsString("Username and password are required")));
    }

    @Test
    @DisplayName("Should reject command without password")
    void shouldRejectCommandWithoutPassword() throws Exception {
        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"command\":\"list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(containsString("Username and password are required")));
    }

    @Test
    @DisplayName("Should reject command with invalid credentials")
    void shouldRejectCommandWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wrong\",\"password\":\"wrong\",\"command\":\"list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(containsString("Invalid username or password")));
    }

    @Test
    @DisplayName("Should reject empty command")
    void shouldRejectEmptyCommand() throws Exception {
        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\",\"command\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(containsString("Command cannot be empty")));
    }

    @Test
    @DisplayName("Should reject null command")
    void shouldRejectNullCommand() throws Exception {
        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(containsString("Command cannot be empty")));
    }
}
