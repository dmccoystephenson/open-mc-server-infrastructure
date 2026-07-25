package com.openmc.minecraftwrapper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BearerTokenAuthenticator Tests")
class BearerTokenAuthenticatorTest {

    private static final String TOKEN = "s3cret-deploy-token";
    private static final String ENDPOINT = "plugin deploy";

    @Test
    @DisplayName("Should authorize a header carrying the exact configured token")
    void shouldAuthorizeExactMatch() {
        assertTrue(BearerTokenAuthenticator.isAuthorized("Bearer " + TOKEN, TOKEN, ENDPOINT));
    }

    @Test
    @DisplayName("Should fail closed when the configured token is null")
    void shouldRejectWhenConfiguredTokenIsNull() {
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer " + TOKEN, null, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer ", null, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized(null, null, ENDPOINT));
    }

    @Test
    @DisplayName("Should fail closed when the configured token is empty or whitespace")
    void shouldRejectWhenConfiguredTokenIsBlank() {
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer " + TOKEN, "", ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer " + TOKEN, "   ", ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer " + TOKEN, "\t", ENDPOINT));
        // An unconfigured server must reject even a request presenting the empty token itself
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer ", "", ENDPOINT));
    }

    @Test
    @DisplayName("Should reject a request with no Authorization header")
    void shouldRejectMissingHeader() {
        assertFalse(BearerTokenAuthenticator.isAuthorized(null, TOKEN, ENDPOINT));
    }

    @Test
    @DisplayName("Should reject a header that does not use the 'Bearer ' prefix")
    void shouldRejectHeaderWithoutBearerPrefix() {
        assertFalse(BearerTokenAuthenticator.isAuthorized(TOKEN, TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("bearer " + TOKEN, TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer" + TOKEN, TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Basic " + TOKEN, TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Token " + TOKEN, TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("", TOKEN, ENDPOINT));
    }

    @Test
    @DisplayName("Should reject an empty token after the Bearer prefix")
    void shouldRejectEmptyTokenAfterPrefix() {
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer ", TOKEN, ENDPOINT));
    }

    @Test
    @DisplayName("Should reject a token that is only a prefix or suffix of the configured one")
    void shouldRejectPartialToken() {
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer s3cret", TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer deploy-token", TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer s3cret-deploy-toke", TOKEN, ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer s3cret-deploy-tokens", TOKEN, ENDPOINT));
    }

    @Test
    @DisplayName("Should reject a token differing only in case")
    void shouldRejectTokenDifferingInCase() {
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer S3CRET-DEPLOY-TOKEN", TOKEN, ENDPOINT));
    }

    @Test
    @DisplayName("Should not trim the configured token when comparing")
    void shouldNotTrimConfiguredTokenWhenComparing() {
        // The blank check trims, but the comparison must use the configured value verbatim.
        assertTrue(BearerTokenAuthenticator.isAuthorized("Bearer  padded ", " padded ", ENDPOINT));
        assertFalse(BearerTokenAuthenticator.isAuthorized("Bearer padded", " padded ", ENDPOINT));
    }
}
