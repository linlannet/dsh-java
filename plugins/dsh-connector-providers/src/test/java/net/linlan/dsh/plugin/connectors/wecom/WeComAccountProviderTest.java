package net.linlan.dsh.plugin.connectors.wecom;

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

class WeComAccountProviderTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void connectReturnsAuthorizedGrantWithExpiry() {
        WeComAccountProvider provider = new WeComAccountProvider("corp", "secret",
                (a, s) -> new WeComAccountProvider.Token("t-abc", 7200), FIXED);

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
        WeComAccountProvider provider = new WeComAccountProvider("corp", "secret",
                (a, s) -> {
                    calls.accumulateAndGet(1, Integer::sum);
                    return new WeComAccountProvider.Token("t-" + calls.get(), 3600);
                }, FIXED);

        ConnectorConnection first = provider.connect();
        ConnectorConnection refreshed = provider.refresh(first.tokenGrant());

        assertEquals("t-2", refreshed.tokenGrant().accessToken());
    }

    @Test
    void missingCredentialsRejectedBeforeCallingEndpoint() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        WeComAccountProvider provider = new WeComAccountProvider("  ", null,
                (a, s) -> { called.set(true); return new WeComAccountProvider.Token("x", 1); }, FIXED);

        ConnectorAccountException ex = assertThrows(ConnectorAccountException.class, provider::connect);

        assertEquals("WECOM_CREDENTIALS_REQUIRED", ex.errorCode());
        assertFalse(ex.retryable());
        assertFalse(called.get());
    }

    @Test
    void clientExceptionMappedToFetchFailed() {
        WeComAccountProvider provider = new WeComAccountProvider("corp", "secret",
                (a, s) -> { throw new RuntimeException("boom"); }, FIXED);

        ConnectorAccountException ex = assertThrows(ConnectorAccountException.class, provider::connect);

        assertEquals("WECOM_TOKEN_FETCH_FAILED", ex.errorCode());
        assertTrue(ex.retryable());
    }

    @Test
    void jdkClientParsesTokenFromRealHttpResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/gettoken", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] body = "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"t-real\",\"expires_in\":7200}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/gettoken";
            WeComAccountProvider.JdkHttpTokenClient client =
                    new WeComAccountProvider.JdkHttpTokenClient(url);

            WeComAccountProvider.Token token = client.fetch("corp", "secret");

            assertEquals("t-real", token.accessToken());
            assertEquals(7200, token.expiresInSeconds());
            assertNotNull(query.get());
            assertTrue(query.get().contains("corpid=corp"));
            assertTrue(query.get().contains("corpsecret=secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jdkClientMapsRejectionCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gettoken", exchange -> {
            byte[] body = "{\"errcode\":40013,\"errmsg\":\"invalid corpid\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/gettoken";
            WeComAccountProvider.JdkHttpTokenClient client =
                    new WeComAccountProvider.JdkHttpTokenClient(url);

            ConnectorAccountException ex =
                    assertThrows(ConnectorAccountException.class, () -> client.fetch("a", "s"));
            assertEquals("WECOM_TOKEN_REJECTED", ex.errorCode());
            assertFalse(ex.retryable());
        } finally {
            server.stop(0);
        }
    }
}
