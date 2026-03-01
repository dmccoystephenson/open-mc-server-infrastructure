package com.openmc.agentmanager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogSanitizerService Tests")
class LogSanitizerServiceTest {

    private final LogSanitizerService sanitizer = new LogSanitizerService();

    @Test
    @DisplayName("Should redact IPv4 address from log line")
    void shouldRedactIpv4Address() {
        String line = "[13:50:33] [Server thread/INFO]: Steve[/192.168.1.100:12345] logged in";
        String result = sanitizer.sanitize(line);
        assertFalse(result.contains("192.168.1.100"));
        assertTrue(result.contains("[IP_REDACTED]"));
        assertTrue(result.contains("Steve"));
    }

    @Test
    @DisplayName("Should redact multiple IPv4 addresses in a single line")
    void shouldRedactMultipleIpv4Addresses() {
        String line = "Connection from 10.0.0.1 forwarded to 10.0.0.2";
        String result = sanitizer.sanitize(line);
        assertFalse(result.contains("10.0.0.1"));
        assertFalse(result.contains("10.0.0.2"));
        assertEquals("Connection from [IP_REDACTED] forwarded to [IP_REDACTED]", result);
    }

    @Test
    @DisplayName("Should redact IPv6 address from log line")
    void shouldRedactIpv6Address() {
        // Full (non-compressed) IPv6 as it appears in Minecraft server logs
        String line = "[Server thread/INFO]: Player[/2001:db8:0:0:0:0:0:1:1234] connected";
        String result = sanitizer.sanitize(line);
        assertFalse(result.contains("2001:db8:0:0:0:0:0:1"));
        assertTrue(result.contains("[IP_REDACTED]"));
    }

    @Test
    @DisplayName("Should leave clean log lines unchanged")
    void shouldLeaveCleanLinesUnchanged() {
        String line = "[13:50:33] [Server thread/INFO]: Server started on port 25565";
        String result = sanitizer.sanitize(line);
        assertEquals(line, result);
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInput() {
        assertNull(sanitizer.sanitize(null));
    }

    @Test
    @DisplayName("Should handle empty string")
    void shouldHandleEmptyString() {
        assertEquals("", sanitizer.sanitize(""));
    }

    @Test
    @DisplayName("Should preserve non-IP content around the redacted address")
    void shouldPreserveContextAroundRedactedAddress() {
        String line = "Player Steve logged in from /203.0.113.42:54321 with UUID abc123";
        String result = sanitizer.sanitize(line);
        assertTrue(result.contains("Steve"));
        assertTrue(result.contains("abc123"));
        assertTrue(result.contains("[IP_REDACTED]"));
        assertFalse(result.contains("203.0.113.42"));
    }
}
