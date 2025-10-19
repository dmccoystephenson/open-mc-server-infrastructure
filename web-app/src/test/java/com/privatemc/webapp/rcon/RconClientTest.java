package com.privatemc.webapp.rcon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RconClient Tests")
class RconClientTest {

    @Test
    @DisplayName("Should throw IOException when connecting to invalid host")
    void shouldThrowExceptionWhenConnectingToInvalidHost() {
        assertThrows(IOException.class, () -> {
            new RconClient("invalid-host", 25575, "password");
        });
    }

    @Test
    @DisplayName("Should throw exception when using invalid port")
    void shouldThrowExceptionWhenUsingInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RconClient("localhost", 99999, "password");
        });
    }
}
