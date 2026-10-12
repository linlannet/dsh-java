package net.linlan.dsh.domain.credentials.pkce;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PkceAuthorizerTest {

    @Test
    void shouldAuthorizeAndReturnToken() {
        StubFlow flow = new StubFlow("access-token-123");
        PkceAuthorizer authorizer = new PkceAuthorizer(flow);

        PkceAuthorizer.PkceResult result = authorizer.authorize("client-1", "http://localhost/cb");

        assertNotNull(result);
        assertEquals("access-token-123", result.accessToken());
        assertNotNull(result.codeVerifier());
        assertNotNull(result.codeChallenge());
    }

    @Test
    void shouldGenerateNonBlankCodeVerifier() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        String verifier = authorizer.generateCodeVerifier();

        assertNotNull(verifier);
        assertFalse(verifier.isBlank());
        assertTrue(verifier.length() >= 43);
    }

    @Test
    void shouldGenerateDifferentVerifiers() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        String v1 = authorizer.generateCodeVerifier();
        String v2 = authorizer.generateCodeVerifier();

        assertNotEquals(v1, v2);
    }

    @Test
    void shouldGenerateCodeChallengeFromVerifier() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        String verifier = "test-verifier-1234567890123456789012345678901234567890";
        String challenge = authorizer.generateCodeChallenge(verifier);

        assertNotNull(challenge);
        assertFalse(challenge.isBlank());
    }

    @Test
    void shouldGenerateConsistentChallengeForSameVerifier() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        String verifier = "consistent-verifier-12345678901234567890123456789012";

        String c1 = authorizer.generateCodeChallenge(verifier);
        String c2 = authorizer.generateCodeChallenge(verifier);

        assertEquals(c1, c2);
    }

    @Test
    void shouldRejectNullVerifierOnChallenge() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        assertThrows(IllegalArgumentException.class, () -> authorizer.generateCodeChallenge(null));
    }

    @Test
    void shouldRejectBlankVerifierOnChallenge() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        assertThrows(IllegalArgumentException.class, () -> authorizer.generateCodeChallenge("  "));
    }

    @Test
    void shouldRejectNullClientId() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        assertThrows(IllegalArgumentException.class, () -> authorizer.authorize(null, "http://cb"));
    }

    @Test
    void shouldRejectBlankClientId() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        assertThrows(IllegalArgumentException.class, () -> authorizer.authorize("  ", "http://cb"));
    }

    @Test
    void shouldRejectNullRedirectUri() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        assertThrows(IllegalArgumentException.class, () -> authorizer.authorize("cid", null));
    }

    @Test
    void shouldRejectBlankRedirectUri() {
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"));
        assertThrows(IllegalArgumentException.class, () -> authorizer.authorize("cid", "  "));
    }

    @Test
    void shouldRejectNullFlowPort() {
        assertThrows(IllegalArgumentException.class, () -> new PkceAuthorizer(null));
    }

    @Test
    void shouldPassVerifierAndChallengeToFlowPort() {
        AtomicReference<String> verifierRef = new AtomicReference<>();
        AtomicReference<String> challengeRef = new AtomicReference<>();
        PkceAuthorizer.PkceFlowPort capturingFlow = (clientId, redirectUri, codeVerifier, codeChallenge) -> {
            verifierRef.set(codeVerifier);
            challengeRef.set(codeChallenge);
            return "token";
        };
        PkceAuthorizer authorizer = new PkceAuthorizer(capturingFlow);

        authorizer.authorize("cid", "http://cb");

        assertNotNull(verifierRef.get());
        assertNotNull(challengeRef.get());
        assertFalse(verifierRef.get().isBlank());
        assertFalse(challengeRef.get().isBlank());
    }

    @Test
    void shouldSupportCustomRandom() {
        SecureRandom fixedRandom = new SecureRandom(new byte[]{1, 2, 3});
        PkceAuthorizer authorizer = new PkceAuthorizer(new StubFlow("t"), fixedRandom);

        String v1 = authorizer.generateCodeVerifier();
        assertNotNull(v1);
        assertTrue(v1.length() >= 43);
    }

    static class StubFlow implements PkceAuthorizer.PkceFlowPort {
        final String token;

        StubFlow(String token) {
            this.token = token;
        }

        @Override
        public String exchange(String clientId, String redirectUri, String codeVerifier, String codeChallenge) {
            return token;
        }
    }
}
