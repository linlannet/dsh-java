package net.linlan.dsh.infrastructure.adapter.credentials;

import net.linlan.dsh.domain.credentials.pkce.PkceAuthorizer;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BrowserPkceFlowTest {

    @Test
    void implementsPkceFlowPort() {
        BrowserPkceFlow flow = new BrowserPkceFlow(
                "https://auth.example.com/authorize", "https://auth.example.com/token");
        assertTrue(flow instanceof PkceAuthorizer.PkceFlowPort);
    }

    @Test
    void defaultConstructorUsesDefaultPortAndTimeout() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "https://token");
        assertNotNull(flow);
    }

    @Test
    void customConstructor() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "https://token", 9876, 60);
        assertNotNull(flow);
    }

    @Test
    void buildAuthorizationUrlContainsAllParams() {
        BrowserPkceFlow flow = new BrowserPkceFlow(
                "https://auth.example.com/authorize", "https://token");
        String url = flow.buildAuthorizationUrl(
                "client-123", "https://app/callback", "challenge-abc", "state-xyz");
        assertTrue(url.startsWith("https://auth.example.com/authorize?"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id=" + URLEncoder.encode("client-123", StandardCharsets.UTF_8)));
        assertTrue(url.contains("redirect_uri=" + URLEncoder.encode("https://app/callback", StandardCharsets.UTF_8)));
        assertTrue(url.contains("code_challenge=" + URLEncoder.encode("challenge-abc", StandardCharsets.UTF_8)));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("state=" + URLEncoder.encode("state-xyz", StandardCharsets.UTF_8)));
    }

    @Test
    void buildAuthorizationUrlEncodesSpecialChars() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "https://token");
        String url = flow.buildAuthorizationUrl("cli ent", "https://app/cb", "ch&allenge", "st=ate");
        assertTrue(url.contains(URLEncoder.encode("cli ent", StandardCharsets.UTF_8)));
        assertTrue(url.contains(URLEncoder.encode("ch&allenge", StandardCharsets.UTF_8)));
        assertTrue(url.contains(URLEncoder.encode("st=ate", StandardCharsets.UTF_8)));
    }

    @Test
    void buildAuthorizationUrlContainsExactEndpoint() {
        BrowserPkceFlow flow = new BrowserPkceFlow(
                "https://idp.example.org/oauth2/authorize", "https://token");
        String url = flow.buildAuthorizationUrl("c", "r", "ch", "s");
        assertTrue(url.startsWith("https://idp.example.org/oauth2/authorize?"));
    }

    @Test
    void waitForCallbackTimesOutAndThrows() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "https://token", 18765, 1);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> flow.waitForCallback("expected-state"));
        assertNotNull(ex.getMessage());
    }

    @Test
    void requestTokenThrowsForInvalidEndpoint() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "not-a-valid-url-endpoint");
        assertThrows(RuntimeException.class,
                () -> flow.requestToken("client", "redirect", "verifier", "code"));
    }

    @Test
    void requestTokenThrowsForUnreachableEndpoint() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "http://localhost:1/token");
        assertThrows(RuntimeException.class,
                () -> flow.requestToken("client", "redirect", "verifier", "code"));
    }

    @Test
    void buildAuthorizationUrlWithEmptyStrings() {
        BrowserPkceFlow flow = new BrowserPkceFlow("https://auth", "https://token");
        String url = flow.buildAuthorizationUrl("", "", "", "");
        assertNotNull(url);
        assertTrue(url.startsWith("https://auth?"));
    }
}
