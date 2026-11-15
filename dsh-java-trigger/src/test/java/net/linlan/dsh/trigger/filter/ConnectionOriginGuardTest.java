package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionOriginGuardTest {

    @Test
    void inactiveGuardAllowsCrossOrigin() {
        ConnectionOriginGuard guard = new ConnectionOriginGuard(false, "");

        assertTrue(guard.allow(request("https://evil.example")));
    }

    @Test
    void activeGuardAllowsSameOrigin() {
        ConnectionOriginGuard guard = new ConnectionOriginGuard(true, "");

        assertTrue(guard.allow(request("http://localhost:8080")));
    }

    @Test
    void activeGuardRejectsCrossOrigin() {
        ConnectionOriginGuard guard = new ConnectionOriginGuard(true, "");

        assertFalse(guard.allow(request("https://evil.example")));
    }

    @Test
    void activeGuardAllowsConfiguredOrigin() {
        ConnectionOriginGuard guard = new ConnectionOriginGuard(true, "https://console.example, https://peer.example");

        assertTrue(guard.allow(request("https://peer.example")));
    }

    @Test
    void requestWithoutOriginIsAllowed() {
        ConnectionOriginGuard guard = new ConnectionOriginGuard(true, "");

        assertTrue(guard.allow(request(null)));
    }

    private HttpServletRequest request(String origin) {
        Map<String, String> headers = origin == null ? Map.of() : Map.of("Origin", origin);
        return ServletTestSupport.request("GET", "/.well-known/agent-card.json", headers, null);
    }
}
