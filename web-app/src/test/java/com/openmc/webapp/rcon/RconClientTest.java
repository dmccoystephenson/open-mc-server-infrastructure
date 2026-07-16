package com.openmc.webapp.rcon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    @DisplayName("Should close its socket when RCON authentication fails (no FD leak)")
    void shouldCloseSocketWhenAuthenticationFails() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            // The fake RCON server completes the auth handshake with a failure
            // (requestId == -1), then reports what it observes afterwards:
            //   -1                 -> client closed the socket (EOF): fix works
            //   Integer.MIN_VALUE  -> still open after timeout: descriptor leaked
            Future<Integer> postAuthRead = executor.submit(() -> {
                try (Socket client = server.accept()) {
                    DataInputStream sin = new DataInputStream(client.getInputStream());
                    DataOutputStream sout = new DataOutputStream(client.getOutputStream());

                    // Consume the client's auth request: 4-byte LE length, then that many bytes.
                    int requestLength = readLittleEndianInt(sin);
                    sin.readFully(new byte[requestLength]);

                    // Reply with an auth-failure packet: size=10, id=-1, type=2, two null bytes.
                    ByteBuffer response = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN);
                    response.putInt(10);
                    response.putInt(-1);
                    response.putInt(2);
                    response.put((byte) 0);
                    response.put((byte) 0);
                    sout.write(response.array());
                    sout.flush();

                    // A correctly-behaving client closes the socket once auth fails.
                    client.setSoTimeout(5000);
                    try {
                        return sin.read(); // -1 == EOF == socket closed by client
                    } catch (SocketTimeoutException timeout) {
                        return Integer.MIN_VALUE; // still open: descriptor leaked
                    }
                }
            });

            assertThrows(IOException.class, () -> new RconClient("localhost", port, "wrong-password"));

            int observed = postAuthRead.get(10, TimeUnit.SECONDS);
            assertEquals(-1, observed,
                    "RconClient must close its socket after an auth failure; the server should "
                            + "observe EOF rather than a still-open connection.");
        } finally {
            executor.shutdownNow();
        }
    }

    private static int readLittleEndianInt(DataInputStream in) throws IOException {
        byte[] bytes = new byte[4];
        in.readFully(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
