package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.service.AlertService;
import com.openmc.minecraftwrapper.service.WorldUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorldUploadController Tests")
class WorldUploadControllerTest {

    private static final String VALID_TOKEN = "test-world-token";
    private static final String UPLOAD_URL = "/api/world/upload";

    @Mock
    private WorldUploadService worldUploadService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private WorldUploadController worldUploadController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worldUploadController, "deployAuthToken", VALID_TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(worldUploadController).build();
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should upload world and return 200 OK with valid token")
    void shouldUploadWorldWithValidToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "world.zip",
                "application/zip", "zip-content".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("World uploaded successfully"));

        verify(worldUploadService, times(1)).replaceWorld(any());
        verify(alertService, times(1)).sendWorldUploadSuccessAlert();
    }

    // ── authentication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 401 when Authorization header is missing")
    void shouldReturn401WhenAuthHeaderMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "world.zip",
                "application/zip", "zip-content".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL).file(file))
                .andExpect(status().isUnauthorized());

        verify(worldUploadService, never()).replaceWorld(any());
    }

    @Test
    @DisplayName("Should return 401 when token is wrong")
    void shouldReturn401WhenTokenIsWrong() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "world.zip",
                "application/zip", "zip-content".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());

        verify(worldUploadService, never()).replaceWorld(any());
    }

    @Test
    @DisplayName("Should return 401 when token is not configured")
    void shouldReturn401WhenTokenNotConfigured() throws Exception {
        ReflectionTestUtils.setField(worldUploadController, "deployAuthToken", "");

        MockMultipartFile file = new MockMultipartFile("file", "world.zip",
                "application/zip", "zip-content".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isUnauthorized());

        verify(worldUploadService, never()).replaceWorld(any());
    }

    @Test
    @DisplayName("Should return 401 when Authorization header has no Bearer prefix")
    void shouldReturn401WhenNoBearerPrefix() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "world.zip",
                "application/zip", "zip-content".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isUnauthorized());

        verify(worldUploadService, never()).replaceWorld(any());
    }

    // ── service error handling ────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 400 when service throws IllegalArgumentException")
    void shouldReturn400OnIllegalArgument() throws Exception {
        doThrow(new IllegalArgumentException("Uploaded file is not a valid ZIP archive"))
                .when(worldUploadService).replaceWorld(any());

        MockMultipartFile file = new MockMultipartFile("file", "world.jar",
                "application/octet-stream", "not-a-zip".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest());

        verify(alertService, times(1)).sendWorldUploadFailureAlert(anyString());
    }

    @Test
    @DisplayName("Should return 500 when service throws IOException")
    void shouldReturn500OnIOException() throws Exception {
        doThrow(new IOException("World directory does not exist"))
                .when(worldUploadService).replaceWorld(any());

        MockMultipartFile file = new MockMultipartFile("file", "world.zip",
                "application/zip", "zip-content".getBytes());

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(file)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isInternalServerError());

        verify(alertService, times(1)).sendWorldUploadFailureAlert(anyString());
    }
}
