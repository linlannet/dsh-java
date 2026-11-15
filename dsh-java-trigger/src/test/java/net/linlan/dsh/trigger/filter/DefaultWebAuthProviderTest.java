package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import net.linlan.dsh.api.IDeviceApi;
import net.linlan.dsh.api.response.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWebAuthProviderTest {

    @Test
    void apiKeyHeaderAuthenticates() {
        DefaultWebAuthProvider provider = provider("key-1", true);
        HttpServletRequest request = request(Map.of("X-API-Key", "key-1"), null);

        assertEquals("header", provider.authenticate(request).orElseThrow().source());
    }

    @Test
    void bearerTokenAuthenticates() {
        DefaultWebAuthProvider provider = provider("key-1", true);
        HttpServletRequest request = request(Map.of("Authorization", "Bearer key-1"), null);

        assertTrue(provider.authenticate(request).isPresent());
    }

    @Test
    void cookieAuthenticatesRequest() {
        DefaultWebAuthProvider provider = provider("key-1", true);
        HttpServletRequest request = request(Map.of(), new Cookie[]{new Cookie("dsh_session", "key-1")});

        assertEquals("cookie", provider.authenticate(request).orElseThrow().source());
    }

    @Test
    void unknownCookieIsRejected() {
        DefaultWebAuthProvider provider = provider("key-1", true);
        HttpServletRequest request = request(Map.of(), new Cookie[]{new Cookie("dsh_session", "other")});

        assertTrue(provider.authenticate(request).isEmpty());
    }

    @Test
    void deviceTokenIsValidatedEvenWithoutConfiguredKeys() {
        DefaultWebAuthProvider provider = provider("", true);
        HttpServletRequest request = request(Map.of("X-API-Key", "devt_abc"), null);

        assertTrue(provider.authenticate(request).isPresent());
        assertTrue(provider.presentsDeviceToken(request));
    }

    @Test
    void revokedDeviceTokenIsRejected() {
        DefaultWebAuthProvider provider = provider("", false);
        HttpServletRequest request = request(Map.of("X-API-Key", "devt_abc"), null);

        assertTrue(provider.authenticate(request).isEmpty());
        assertFalse(provider.authenticate(request(Map.of(), null)).isPresent());
    }

    @Test
    void issueSessionTokenOnlyAcceptsKnownCredential() {
        DefaultWebAuthProvider provider = provider("key-1", true);

        assertEquals("key-1", provider.issueSessionToken(" key-1 ").orElseThrow());
        assertTrue(provider.issueSessionToken("other").isEmpty());
        assertTrue(provider.issueSessionToken("").isEmpty());
    }

    private DefaultWebAuthProvider provider(String keys, boolean deviceValid) {
        return new DefaultWebAuthProvider(deviceApi(deviceValid), keys, "dsh_session");
    }

    private HttpServletRequest request(Map<String, String> headers, Cookie[] cookies) {
        return ServletTestSupport.request("GET", "/api/agent/message", headers, cookies);
    }

    private IDeviceApi deviceApi(boolean valid) {
        return (IDeviceApi) Proxy.newProxyInstance(
                DefaultWebAuthProviderTest.class.getClassLoader(),
                new Class<?>[]{IDeviceApi.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "validateDeviceToken" -> Response.success(valid);
                    case "toString" -> "device-api";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
