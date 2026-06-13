package com.openmc.webapp.controller;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.service.ActivityTrackerService;
import com.openmc.webapp.service.AlertNotificationService;
import com.openmc.webapp.service.PluginService;
import com.openmc.webapp.service.RconService;
import com.openmc.webapp.service.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    
    @MockBean
    private ActivityTrackerService activityTrackerService;
    
    @MockBean
    private PluginService pluginService;

    @MockBean
    private WorldService worldService;
    
    @MockBean
    private AlertNotificationService alertNotificationService;
    
    @MockBean
    private com.openmc.webapp.service.MinecraftWrapperService minecraftWrapperService;
    
    @MockBean
    private com.openmc.webapp.service.DeploymentHistoryService deploymentHistoryService;

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
        when(activityTrackerService.isEnabled()).thenReturn(false);
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
        
        // Verify alert was sent
        verify(alertNotificationService, times(1)).sendInfoAlert(
            anyString(), 
            anyString()
        );
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
        
        // Verify warning alert was sent for failed authentication
        verify(alertNotificationService, times(1)).sendWarningAlert(
            anyString(), 
            anyString()
        );
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
    @DisplayName("Should list plugins with valid credentials")
    void shouldListPluginsWithValidCredentials() throws Exception {
        List<String> plugins = Arrays.asList("plugin1.jar", "plugin2.jar");
        when(pluginService.listPlugins()).thenReturn(plugins);
        
        mockMvc.perform(post("/api/plugins/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.plugins[0]").value("plugin1.jar"))
                .andExpect(jsonPath("$.plugins[1]").value("plugin2.jar"));
    }
    
    @Test
    @DisplayName("Should reject list plugins with invalid credentials")
    void shouldRejectListPluginsWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/plugins/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wrong\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("Invalid username or password")));
    }
    
    @Test
    @DisplayName("Should upload plugin with valid credentials")
    void shouldUploadPluginWithValidCredentials() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-plugin.jar",
            "application/java-archive",
            "test content".getBytes()
        );
        
        when(pluginService.uploadPlugin(any())).thenReturn("Plugin uploaded successfully: test-plugin.jar");
        
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("uploaded successfully")));
        
        // Verify alert was sent for successful upload
        verify(alertNotificationService, times(1)).sendInfoAlert(
            anyString(), 
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should reject upload plugin with invalid credentials")
    void shouldRejectUploadPluginWithInvalidCredentials() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-plugin.jar",
            "application/java-archive",
            "test content".getBytes()
        );
        
        mockMvc.perform(multipart("/api/plugins/upload")
                        .file(file)
                        .param("username", "wrong")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("Invalid username or password")));
        
        // Verify warning alert was sent for failed authentication
        verify(alertNotificationService, times(1)).sendWarningAlert(
            anyString(), 
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should delete plugin with valid credentials")
    void shouldDeletePluginWithValidCredentials() throws Exception {
        when(pluginService.deletePlugin(anyString())).thenReturn("Plugin deleted successfully: test-plugin.jar");
        
        mockMvc.perform(post("/api/plugins/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\",\"filename\":\"test-plugin.jar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("deleted successfully")));
        
        // Verify alert was sent for successful deletion
        verify(alertNotificationService, times(1)).sendInfoAlert(
            anyString(), 
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should reject delete plugin with invalid credentials")
    void shouldRejectDeletePluginWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/plugins/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wrong\",\"password\":\"wrong\",\"filename\":\"test-plugin.jar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("Invalid username or password")));
        
        // Verify warning alert was sent for failed authentication
        verify(alertNotificationService, times(1)).sendWarningAlert(
            anyString(), 
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should return player profile page when player exists")
    void shouldReturnPlayerProfilePageWhenPlayerExists() throws Exception {
        com.openmc.webapp.model.PlayerProfile mockProfile = new com.openmc.webapp.model.PlayerProfile(
            "550e8400-e29b-41d4-a716-446655440000",
            "TestPlayer",
            123.5,
            50,
            1
        );
        
        when(activityTrackerService.getPlayerProfile("TestPlayer")).thenReturn(mockProfile);
        
        mockMvc.perform(get("/player/TestPlayer"))
                .andExpect(status().isOk())
                .andExpect(view().name("player"))
                .andExpect(model().attributeExists("profile"))
                .andExpect(model().attribute("profile", mockProfile));
    }
    
    @Test
    @DisplayName("Should return error page when player not found")
    void shouldReturnErrorPageWhenPlayerNotFound() throws Exception {
        when(activityTrackerService.getPlayerProfile("UnknownPlayer")).thenReturn(null);
        
        mockMvc.perform(get("/player/UnknownPlayer"))
                .andExpect(status().isOk())
                .andExpect(view().name("player"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attributeExists("playerName"));
    }
    
    @Test
    @DisplayName("Should return player profile via API when player exists")
    void shouldReturnPlayerProfileViaApiWhenPlayerExists() throws Exception {
        com.openmc.webapp.model.PlayerProfile mockProfile = new com.openmc.webapp.model.PlayerProfile(
            "550e8400-e29b-41d4-a716-446655440000",
            "TestPlayer",
            123.5,
            50,
            1
        );
        
        when(activityTrackerService.getPlayerProfile("TestPlayer")).thenReturn(mockProfile);
        
        mockMvc.perform(get("/api/player/TestPlayer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("TestPlayer"))
                .andExpect(jsonPath("$.playerUuid").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.hoursPlayed").value(123.5))
                .andExpect(jsonPath("$.totalLogins").value(50))
                .andExpect(jsonPath("$.leaderboardRank").value(1));
    }
    
    @Test
    @DisplayName("Should return 404 via API when player not found")
    void shouldReturn404ViaApiWhenPlayerNotFound() throws Exception {
        when(activityTrackerService.getPlayerProfile("UnknownPlayer")).thenReturn(null);
        
        mockMvc.perform(get("/api/player/UnknownPlayer"))
                .andExpect(status().isNotFound());
    }
}
