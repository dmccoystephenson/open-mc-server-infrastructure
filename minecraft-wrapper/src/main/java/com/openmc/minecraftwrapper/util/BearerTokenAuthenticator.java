package com.openmc.minecraftwrapper.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class BearerTokenAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticator.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenAuthenticator() {}

    /**
     * Returns {@code true} if {@code authHeader} carries a Bearer token that matches
     * {@code configuredToken} using a constant-time comparison.
     *
     * <p>Returns {@code false} when {@code configuredToken} is blank so that an
     * unconfigured server always rejects requests.
     *
     * @param authHeader      the value of the HTTP {@code Authorization} header (may be null)
     * @param configuredToken the expected token from application config (may be null/blank)
     * @param endpointName    logged in the warning when the token is unconfigured
     */
    public static boolean isAuthorized(String authHeader, String configuredToken, String endpointName) {
        if (configuredToken == null || configuredToken.trim().isEmpty()) {
            log.warn("deploy.auth.token is not configured; all {} requests will be rejected", endpointName);
            return false;
        }
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String provided = authHeader.substring(BEARER_PREFIX.length());
        return MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
