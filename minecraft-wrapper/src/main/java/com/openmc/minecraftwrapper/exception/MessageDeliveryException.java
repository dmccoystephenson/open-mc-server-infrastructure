package com.openmc.minecraftwrapper.exception;

/**
 * Thrown when a message accepted by {@code POST /api/messages} could not be
 * handed to the alert manager.
 *
 * <p>The wrapper does not deliver messages itself — it forwards them to the
 * alert manager, which fans them out to Discord and to the game over RCON. If
 * that forward fails there is nothing left to retry and nothing was delivered,
 * so the caller has to be told. Swallowing the failure and answering 200 is
 * what this exception exists to prevent.</p>
 */
public class MessageDeliveryException extends RuntimeException {

    public MessageDeliveryException(String message) {
        super(message);
    }

    public MessageDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
