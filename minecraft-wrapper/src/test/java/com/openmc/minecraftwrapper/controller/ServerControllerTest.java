package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.model.ServerMetrics;
import com.openmc.minecraftwrapper.model.ServerStatus;
import com.openmc.minecraftwrapper.service.MinecraftServerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ServerController.class)
@DisplayName("ServerController Tests")
class ServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MinecraftServerService minecraftServerService;

    @Test
    @DisplayName("Should return server status")
    void shouldReturnServerStatus() throws Exception {
        ServerStatus status = ServerStatus.builder()
                .running(true)
                .pid(12345L)
                .serverJar("server.jar")
                .serverDirectory("/minecraft")
                .uptimeSeconds(3600L)
                .build();
        when(minecraftServerService.getStatus()).thenReturn(status);

        mockMvc.perform(get("/api/server/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.pid").value(12345))
                .andExpect(jsonPath("$.serverJar").value("server.jar"));
    }

    @Test
    @DisplayName("Should return 202 for start request")
    void shouldReturn202ForStartRequest() throws Exception {
        mockMvc.perform(post("/api/server/start"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Server start initiated"));
    }

    @Test
    @DisplayName("Should return 202 for stop request")
    void shouldReturn202ForStopRequest() throws Exception {
        mockMvc.perform(post("/api/server/stop"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Server stop initiated"));
    }

    @Test
    @DisplayName("Should return 202 for restart request")
    void shouldReturn202ForRestartRequest() throws Exception {
        mockMvc.perform(post("/api/server/restart"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Server restart initiated"));
    }

    @Test
    @DisplayName("Should return 202 for shutdown request")
    void shouldReturn202ForShutdownRequest() throws Exception {
        mockMvc.perform(post("/api/server/shutdown"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Should send command successfully")
    void shouldSendCommandSuccessfully() throws Exception {
        mockMvc.perform(post("/api/server/command")
                .contentType(MediaType.TEXT_PLAIN)
                .content("list"))
                .andExpect(status().isOk())
                .andExpect(content().string("Command sent successfully"));

        verify(minecraftServerService).sendCommand("list");
    }

    @Test
    @DisplayName("Should return 400 when command is blank")
    void shouldReturn400WhenCommandIsBlank() throws Exception {
        mockMvc.perform(post("/api/server/command")
                .contentType(MediaType.TEXT_PLAIN)
                .content("   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when server not running for command")
    void shouldReturn400WhenServerNotRunningForCommand() throws Exception {
        doThrow(new IllegalStateException("Server is not running"))
                .when(minecraftServerService).sendCommand(anyString());

        mockMvc.perform(post("/api/server/command")
                .contentType(MediaType.TEXT_PLAIN)
                .content("list"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Server is not running"));
    }

    @Test
    @DisplayName("Should return 500 when command IO fails")
    void shouldReturn500WhenCommandIOFails() throws Exception {
        doThrow(new IOException("pipe broken"))
                .when(minecraftServerService).sendCommand(anyString());

        mockMvc.perform(post("/api/server/command")
                .contentType(MediaType.TEXT_PLAIN)
                .content("list"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to send command"));
    }

    @Test
    @DisplayName("Should return 403 when log access is disabled")
    void shouldReturn403WhenLogAccessDisabled() throws Exception {
        mockMvc.perform(get("/api/server/logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return server metrics")
    void shouldReturnServerMetrics() throws Exception {
        ServerMetrics metrics = ServerMetrics.builder()
                .wrapperHeapUsedMb(256L)
                .wrapperHeapMaxMb(512L)
                .wrapperHeapUsedPercent(50.0)
                .serverUptimeSeconds(3600L)
                .build();
        when(minecraftServerService.getServerMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/server/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wrapperHeapUsedMb").value(256))
                .andExpect(jsonPath("$.wrapperHeapMaxMb").value(512));
    }
}
