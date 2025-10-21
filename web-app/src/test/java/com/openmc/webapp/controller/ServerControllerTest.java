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
        RconService.ResourceUsage mockResourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        mockStatus = new RconService.ServerStatus(serverConfig, "There are 0 of a max of 20 players online", mockResourceUsage);
        
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
    @DisplayName("Should return resource usage on GET /api/resources")
    void shouldReturnResourceUsageOnGetApiResources() throws Exception {
        RconService.ResourceUsage mockResourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        when(rconService.getResourceUsage()).thenReturn(mockResourceUsage);

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tps").value("20.0, 20.0, 20.0"))
                .andExpect(jsonPath("$.memoryUsed").value("1024MB"));
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

    @Test
    @DisplayName("Should successfully change password with valid credentials")
    void shouldChangePasswordWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"currentPassword\":\"admin\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("true"))
                .andExpect(jsonPath("$.message").value(containsString("Password updated successfully")));
    }

    @Test
    @DisplayName("Should reject password change with invalid current password")
    void shouldRejectPasswordChangeWithInvalidCurrentPassword() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"currentPassword\":\"wrongpass\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid username or current password")));
    }

    @Test
    @DisplayName("Should reject password change with invalid username")
    void shouldRejectPasswordChangeWithInvalidUsername() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wronguser\",\"currentPassword\":\"admin\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid username or current password")));
    }

    @Test
    @DisplayName("Should reject password change with missing username")
    void shouldRejectPasswordChangeWithMissingUsername() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"admin\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("All fields are required")));
    }

    @Test
    @DisplayName("Should reject password change with missing current password")
    void shouldRejectPasswordChangeWithMissingCurrentPassword() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("All fields are required")));
    }

    @Test
    @DisplayName("Should reject password change with missing new password")
    void shouldRejectPasswordChangeWithMissingNewPassword() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"currentPassword\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("All fields are required")));
    }

    @Test
    @DisplayName("Should reject password change with empty new password")
    void shouldRejectPasswordChangeWithEmptyNewPassword() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"currentPassword\":\"admin\",\"newPassword\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("New password cannot be empty")));
    }

    @Test
    @DisplayName("Should reject password change with whitespace-only new password")
    void shouldRejectPasswordChangeWithWhitespaceNewPassword() throws Exception {
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"currentPassword\":\"admin\",\"newPassword\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("false"))
                .andExpect(jsonPath("$.message").value(containsString("New password cannot be empty")));
    }

    @Test
    @DisplayName("Should allow command with new password after password change")
    void shouldAllowCommandWithNewPasswordAfterChange() throws Exception {
        // First change the password
        mockMvc.perform(post("/api/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"currentPassword\":\"admin\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("true"));

        // Update the mock to return the new password
        when(serverConfig.getAdminPassword()).thenReturn("newpass123");
        when(rconService.sendCommand("list")).thenReturn("There are 0 of a max of 20 players online");

        // Now try to send a command with the new password
        mockMvc.perform(post("/api/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"newpass123\",\"command\":\"list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists());
    }
}
