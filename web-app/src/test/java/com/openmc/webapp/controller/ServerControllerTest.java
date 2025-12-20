package com.openmc.webapp.controller;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.service.ActivityTrackerService;
import com.openmc.webapp.service.PluginService;
import com.openmc.webapp.service.RconService;
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
    @DisplayName("Should list plugins with valid credentials")
    void shouldListPluginsWithValidCredentials() throws Exception {
        List<String> plugins = Arrays.asList("plugin1.jar", "plugin2.jar");
        when(pluginService.listPlugins()).thenReturn(plugins);
        
        mockMvc.perform(get("/api/plugins/list")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.plugins[0]").value("plugin1.jar"))
                .andExpect(jsonPath("$.plugins[1]").value("plugin2.jar"));
    }
    
    @Test
    @DisplayName("Should reject list plugins with invalid credentials")
    void shouldRejectListPluginsWithInvalidCredentials() throws Exception {
        mockMvc.perform(get("/api/plugins/list")
                        .param("username", "wrong")
                        .param("password", "wrong"))
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
    }
}
