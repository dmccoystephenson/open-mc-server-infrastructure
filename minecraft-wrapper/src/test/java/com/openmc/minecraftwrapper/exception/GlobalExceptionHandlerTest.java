package com.openmc.minecraftwrapper.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should return 400 with generic message for method argument validation errors")
    void shouldHandleValidationErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().get("message"));
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Bad Request", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("Should return 400 with the violation message for constraint violations")
    void shouldHandleConstraintViolation() {
        ConstraintViolationException ex = new ConstraintViolationException("message must not be blank", Collections.emptySet());

        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("message must not be blank", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Should return 400 with a fixed message for malformed request bodies")
    void shouldHandleHttpMessageNotReadable() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Malformed request body", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Should return 400 with generic message for handler method validation errors")
    void shouldHandleHandlerMethodValidation() {
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);

        ResponseEntity<Map<String, Object>> response = handler.handleHandlerMethodValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Should return 400 with the exception message for invalid arguments")
    void shouldHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Plugin name must not contain path separators");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Plugin name must not contain path separators", response.getBody().get("message"));
        assertEquals(400, response.getBody().get("status"));
    }

    @Test
    @DisplayName("Should return 409 with the exception message for invalid server state")
    void shouldHandleIllegalState() {
        IllegalStateException ex = new IllegalStateException("Server is already running");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Server is already running", response.getBody().get("message"));
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Conflict", response.getBody().get("error"));
    }

    @Test
    @DisplayName("Should return 500 with a fixed message and hide details for I/O errors")
    void shouldHandleIOException() {
        IOException ex = new IOException("/mcserver/plugins/secret-path denied");

        ResponseEntity<Map<String, Object>> response = handler.handleIOException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An I/O error occurred", response.getBody().get("message"));
        assertEquals(500, response.getBody().get("status"));
        assertFalse(response.getBody().get("message").toString().contains("secret-path"));
    }

    @Test
    @DisplayName("Should return 500 with a fixed message and hide details for unexpected exceptions")
    void shouldHandleGenericException() {
        RuntimeException ex = new RuntimeException("some internal secret detail");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().get("message"));
        assertFalse(response.getBody().get("message").toString().contains("secret"));
    }

    @Test
    @DisplayName("Should build every error body with the same ordered set of keys")
    void shouldBuildConsistentErrorBody() {
        List<Map<String, Object>> bodies = new ArrayList<>();
        bodies.add(handler.handleIllegalArgument(new IllegalArgumentException("bad")).getBody());
        bodies.add(handler.handleIllegalState(new IllegalStateException("conflict")).getBody());
        bodies.add(handler.handleIOException(new IOException("io")).getBody());
        bodies.add(handler.handleGenericException(new RuntimeException("boom")).getBody());

        for (Map<String, Object> body : bodies) {
            assertNotNull(body);
            assertIterableEquals(List.of("timestamp", "status", "error", "message"), body.keySet());
        }
    }
}
