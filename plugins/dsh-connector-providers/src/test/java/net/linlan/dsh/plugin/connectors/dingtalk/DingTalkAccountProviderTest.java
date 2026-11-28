package net.linlan.dsh.plugin.connectors.dingtalk;

import com.sun.net.httpserver.HttpServer;
import net.linlan.dsh.domain.connector.ConnectorAccountException;
import net.linlan.dsh.domain.connector.ConnectorConnection;
import net.linlan.dsh.domain.connector.ConnectorTokenGrant;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkAccountProviderTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void connectReturnsAuthorizedGrantWithExpiry() {
        DingTalkAccountProvider provider = new DingTalkAccountProvider("key", "secret",
                (a, s) -> new DingTalkAccountProvider.Token("t-abc", 7200), FIXED);

        ConnectorConnection conn = provider.connect();

        assertFalse(conn.authorizationRequired());
        ConnectorTokenGrant grant = conn.tokenGrant();
        assertEquals("t-abc", grant.accessToken());
        assertNull(grant.refreshToken());
        assertEquals(Instant.parse("2026-01-01T02:00:00Z"), grant.expiresAt());
    }

    @Test
    void refreshRefetchesToken() {
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        DingTalkAccountProvider provider = new DingTalkAccountProvider("key", "secret",
                (a, s) -> {
                    calls.accumulateAndGet(1, Integer::sum);
                    return new DingTalkAccountProvider.Token("t-" + calls.get(), 3600);
                }, FIXED);

        ConnectorConnection first = provider.connect();
        ConnectorConnection refreshed = provider.refresh(first.tokenGrant());

        assertEquals("t-2", refreshed.tokenGrant().accessToken());
    }

    @Test
    void missingCredentialsRejectedBeforeCallingEndpoint() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        DingTalkAccountProvider provider = new DingTalkAccountProvider("  ", null,
                (a, s) -> { called.set(true); return new DingTalkAccountProvider.Token("x", 1); }, FIXED);

        ConnectorAccountException ex = assertThrows(ConnectorAccountException.class, provider::connect);

        assertEquals("DINGTALK_CREDENTIALS_REQUIRED", ex.errorCode());
        assertFalse(ex.retryable());
        assertFalse(called.get());
    }

    @Test
    void clientExceptionMappedToFetchFailed() {
        DingTalkAccountProvider provider = new DingTalkAccountProvider("key", "secret",
                (a, s) -> { throw new RuntimeException("boom"); }, FIXED);

        ConnectorAccountException ex = assertThrows(ConnectorAccountException.class, provider::connect);

        assertEquals("DINGTALK_TOKEN_FETCH_FAILED", ex.errorCode());
        assertTrue(ex.retryable());
    }

    @Test
    void jdkClientParsesTokenFromRealHttpResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext("/token", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"accessToken\":\"t-real\",\"expireIn\":7200}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
            DingTalkAccountProvider.JdkHttpTokenClient client =
                    new DingTalkAccountProvider.JdkHttpTokenClient(url);

            DingTalkAccountProvider.Token token = client.fetch("key", "secret");

            assertEquals("t-real", token.accessToken());
            assertEquals(7200, token.expiresInSeconds());
            assertNotNull(received.get());
            assertTrue(received.get().contains("\"appKey\":\"key\""));
            assertTrue(received.get().contains("\"appSecret\":\"secret\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientMapsHttpClientErrorWithVendorCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = "{\"code\":\"InvalidAppKey\",\"message\":\"appKey invalid\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
            DingTalkAccountProvider.JdkHttpTokenClient client =
                    new DingTalkAccountProvider.JdkHttpTokenClient(url);

            ConnectorAccountException ex =
                    assertThrows(ConnectorAccountException.class, () -> client.fetch("a", "s"));
            assertEquals("DINGTALK_TOKEN_REJECTED", ex.errorCode());
            assertFalse(ex.retryable());
            assertTrue(ex.getMessage().contains("InvalidAppKey"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientMapsMissingToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = "{\"code\":\"InvalidAppKey\",\"message\":\"appKey invalid\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
            DingTalkAccountProvider.JdkHttpTokenClient client =
                    new DingTalkAccountProvider.JdkHttpTokenClient(url);

            ConnectorAccountException ex =
                    assertThrows(ConnectorAccountException.class, () -> client.fetch("a", "s"));
            assertEquals("DINGTALK_TOKEN_REJECTED", ex.errorCode());
            assertFalse(ex.retryable());
        } finally {
            server.stop(0);
        }
    }
}
