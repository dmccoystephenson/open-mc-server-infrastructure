package com.openmc.webapp.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

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
        ConstraintViolationException ex = new ConstraintViolationException("command must not be blank", Collections.emptySet());

        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("command must not be blank", response.getBody().get("message"));
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
    @DisplayName("Should return 400 with a fixed message for malformed request bodies")
    void shouldHandleHttpMessageNotReadable() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Malformed request body", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Should return 500 with a fixed message and hide details for unexpected exceptions")
    void shouldHandleGenericException() {
        RuntimeException ex = new RuntimeException("rcon password was hunter2");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().get("message"));
        assertEquals(500, response.getBody().get("status"));
        assertEquals("Internal Server Error", response.getBody().get("error"));
        assertFalse(response.getBody().get("message").toString().contains("hunter2"));
    }

    @Test
    @DisplayName("Should build every error body with the same ordered set of keys")
    void shouldBuildConsistentErrorBody() {
        Map<String, Object> clientError = handler
                .handleConstraintViolation(new ConstraintViolationException("bad", Collections.emptySet()))
                .getBody();
        Map<String, Object> serverError = handler.handleGenericException(new RuntimeException("boom")).getBody();

        assertNotNull(clientError);
        assertNotNull(serverError);
        assertIterableEquals(List.of("timestamp", "status", "error", "message"), clientError.keySet());
        assertIterableEquals(List.of("timestamp", "status", "error", "message"), serverError.keySet());
    }
}
