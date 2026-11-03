package net.linlan.dsh.infrastructure.adapter.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 设备授权适配器测试：以本地 {@link HttpServer} 承接请求，校验 begin/poll 语义，不访问真实飞书。
 */
class FeishuPairingClientTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final AtomicReference<String> nextResponse = new AtomicReference<>("{}");
    private static final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private static final AtomicReference<String> lastBody = new AtomicReference<>("");

    private final FeishuPairingClient client = new FeishuPairingClient(new ObjectMapper());

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/v1/app/registration", FeishuPairingClientTest::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        lastBody.set(body);
        byte[] payload = nextResponse.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(nextStatus.get(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    @Test
    void beginBuildsPairingUrlWithSourceAndNames() {
        nextStatus.set(200);
        nextResponse.set("{\"verification_uri_complete\":\"https://accounts.feishu.cn/sign?t=1\","
                + "\"user_code\":\"ABCD-1234\",\"device_code\":\"dv_1\",\"expires_in\":1800,\"interval\":7}");

        FeishuPairingBeginVO vo = this.client.begin("我的 Bot", "描述", baseUrl);

        assertTrue(vo.url().startsWith("https://accounts.feishu.cn/sign?t=1&source=dsh-java"), vo.url());
        String decoded = java.net.URLDecoder.decode(vo.url(), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("name=我的 Bot"), decoded);
        assertTrue(decoded.contains("desc=描述"), decoded);
        assertEquals("ABCD-1234", vo.userCode());
        assertEquals("dv_1", vo.deviceCode());
        assertEquals(1800L, vo.expireInSec());
        assertEquals(7, vo.intervalSec());
        assertTrue(lastBody.get().contains("action=begin"), lastBody.get());
        assertTrue(lastBody.get().contains("auth_method=client_secret"), lastBody.get());
    }

    @Test
    void beginFailsWhenVerificationUriMissing() {
        nextStatus.set(200);
        nextResponse.set("{\"user_code\":\"ABCD\"}");
        FeishuPairingClient local = this.client;
        assertThrows(IllegalStateException.class, () -> local.begin("n", "d", baseUrl));
    }

    @Test
    void pollReturnsPendingWhileAuthorizationPending() {
        nextStatus.set(200);
        nextResponse.set("{\"error\":\"authorization_pending\",\"error_description\":\"waiting\"}");

        FeishuPairingPollVO vo = this.client.poll(baseUrl, "dv_1");

        assertFalse(vo.succeeded());
        assertEquals("authorization_pending", vo.error());
        assertEquals("waiting", vo.errorDescription());
    }

    @Test
    void pollReturnsCredentialsOnSuccess() {
        nextStatus.set(200);
        nextResponse.set("{\"client_id\":\"cli_new\",\"client_secret\":\"sec_new\","
                + "\"user_info\":{\"open_id\":\"ou_1\",\"tenant_brand\":\"feishu\"}}");

        FeishuPairingPollVO vo = this.client.poll(baseUrl, "dv_1");

        assertTrue(vo.succeeded());
        assertEquals("cli_new", vo.clientId());
        assertEquals("sec_new", vo.clientSecret());
        assertEquals("ou_1", vo.openId());
        assertEquals("feishu", vo.tenantBrand());
        assertNull(vo.error());
    }

    @Test
    void pollTreatsServerErrorAsPending() {
        nextStatus.set(500);
        nextResponse.set("boom");

        FeishuPairingPollVO vo = this.client.poll(baseUrl, "dv_1");

        assertFalse(vo.succeeded());
        assertEquals("authorization_pending", vo.error(), "HTTP 5xx 应按 pending 处理以继续轮询");
    }

    @Test
    void pollTreatsNetworkFailureAsPending() {
        FeishuPairingPollVO vo = this.client.poll("http://127.0.0.1:1", "dv_1");

        assertFalse(vo.succeeded());
        assertEquals("authorization_pending", vo.error());
        nextStatus.set(200);
    }

    @Test
    void larkUsesOwnAccountDomain() {
        assertEquals("https://accounts.larksuite.com", this.client.larkBaseUrl());
    }
}
