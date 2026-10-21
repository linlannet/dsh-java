package net.linlan.dsh.infrastructure.adapter.translation;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BingTranslatorAdapterTest Bing 翻译适配器单元测试。
 *
 * <p>不访问真实外部网络：成功与异常解析路径由 127.0.0.1 上的进程内 HttpServer 提供，
 * 不可达路径由保留的本地端口 1 触发，降级行为以领域回退原文为准（返回 empty）。</p>
 */
@DisplayName("BingTranslatorAdapter 翻译适配器")
public class BingTranslatorAdapterTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastSubscriptionKey = new AtomicReference<>();

    /** 启动一个仅在回环地址监听的固定响应服务器，并记录收到的订阅 Key 头。 */
    private void startServer(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            lastSubscriptionKey.set(exchange.getRequestHeaders().getFirst("Ocp-Apim-Subscription-Key"));
            exchange.getRequestBody().readAllBytes();
            if (body == null || body.isEmpty()) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(payload);
                }
            }
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + port + "/translate";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // 场景：text 为 null、空白时视为无内容，直接返回 empty 且不发起请求
    @Test
    @DisplayName("空白 text 返回 empty")
    void blankTextReturnsEmpty() {
        BingTranslatorAdapter adapter = new BingTranslatorAdapter("http://127.0.0.1:1/translate", "key");

        assertTrue(adapter.translate(null, "zh").isEmpty(), "null 文本应返回 empty");
        assertTrue(adapter.translate("", "zh").isEmpty(), "空文本应返回 empty");
        assertTrue(adapter.translate("   ", "zh").isEmpty(), "空白文本应返回 empty");
    }

    // 场景：targetLang 为 null、空白时无法确定目标语言，直接返回 empty
    @Test
    @DisplayName("空白 targetLang 返回 empty")
    void blankTargetLangReturnsEmpty() {
        BingTranslatorAdapter adapter = new BingTranslatorAdapter("http://127.0.0.1:1/translate", "key");

        assertTrue(adapter.translate("hello", null).isEmpty(), "null 目标语言应返回 empty");
        assertTrue(adapter.translate("hello", "").isEmpty(), "空目标语言应返回 empty");
        assertTrue(adapter.translate("hello", "  ").isEmpty(), "空白目标语言应返回 empty");
    }

    // 场景：使用单参数构造（默认端点）时空白输入同样短路返回 empty，构造不抛异常
    @Test
    @DisplayName("单参数构造下空白输入短路返回 empty")
    void defaultEndpointConstructorStillShortCircuits() {
        BingTranslatorAdapter adapter = assertDoesNotThrow(() -> new BingTranslatorAdapter("sub-key"));

        assertTrue(adapter.translate("   ", "zh").isEmpty(), "空白文本应在发送请求前返回 empty");
    }

    // 场景：端点不可达时降级返回 empty 且不抛异常
    @Test
    @DisplayName("不可达端点降级返回 empty")
    void unreachableEndpointReturnsEmpty() {
        BingTranslatorAdapter adapter = new BingTranslatorAdapter("http://127.0.0.1:1/translate", "key");

        Optional<String> result = assertDoesNotThrow(() -> adapter.translate("hello", "zh"),
                "连接失败应被捕获而非抛出");
        assertTrue(result.isEmpty(), "不可达端点应返回 empty");
    }

    // 场景：正常响应体为 Bing 标准数组结构时解析出首个译文，并携带订阅 Key
    @Test
    @DisplayName("标准响应解析出译文并携带订阅 Key")
    void successParsesFirstTranslation() throws IOException {
        startServer("[{\"translations\":[{\"text\":\"你好\"}]}]");
        BingTranslatorAdapter adapter = new BingTranslatorAdapter(endpoint(), "sub-key-1");

        Optional<String> result = adapter.translate("hello", "zh");

        assertTrue(result.isPresent(), "标准响应应解析出译文");
        assertEquals("你好", result.get(), "应返回首个译文文本");
        assertEquals("sub-key-1", lastSubscriptionKey.get(), "应携带订阅 Key 头");
    }

    // 场景：端点已带查询串时应以 & 追加目标语言参数（Apache 风格端点复用）
    @Test
    @DisplayName("端点含查询串时以 & 追加参数")
    void endpointWithQueryStringAppendsWithAmpersand() throws IOException {
        startServer("[{\"translations\":[{\"text\":\"bonjour\"}]}]");
        BingTranslatorAdapter adapter =
                new BingTranslatorAdapter(endpoint() + "?api-version=3.0", "sub-key-2");

        Optional<String> result = adapter.translate("hello", "fr");

        assertTrue(result.isPresent(), "含查询串端点应仍可解析译文");
        assertEquals("bonjour", result.get(), "应返回译文文本");
    }

    // 场景：响应体为空时返回 empty
    @Test
    @DisplayName("空响应体返回 empty")
    void blankBodyReturnsEmpty() throws IOException {
        startServer("");

        Optional<String> result = new BingTranslatorAdapter(endpoint(), "key").translate("hello", "zh");

        assertTrue(result.isEmpty(), "空响应体应返回 empty");
    }

    // 场景：响应体为非 JSON 数组结构（对象）时返回 empty
    @Test
    @DisplayName("非数组结构响应返回 empty")
    void nonArrayBodyReturnsEmpty() throws IOException {
        startServer("{\"result\":\"x\"}");

        Optional<String> result = new BingTranslatorAdapter(endpoint(), "key").translate("hello", "zh");

        assertTrue(result.isEmpty(), "非数组结构应返回 empty");
    }

    // 场景：响应体为 JSON 但语法非法时捕获解析异常并返回 empty
    @Test
    @DisplayName("非法 JSON 返回 empty")
    void malformedJsonReturnsEmpty() throws IOException {
        startServer("not-a-json");

        Optional<String> result = new BingTranslatorAdapter(endpoint(), "key").translate("hello", "zh");

        assertTrue(result.isEmpty(), "非法 JSON 应返回 empty");
    }

    // 场景：数组为空（无翻译项）时返回 empty
    @Test
    @DisplayName("空数组响应返回 empty")
    void emptyArrayReturnsEmpty() throws IOException {
        startServer("[]");

        Optional<String> result = new BingTranslatorAdapter(endpoint(), "key").translate("hello", "zh");

        assertTrue(result.isEmpty(), "空数组应返回 empty");
    }

    // 场景：translations 数组为空时返回 empty
    @Test
    @DisplayName("translations 为空数组时返回 empty")
    void emptyTranslationsReturnsEmpty() throws IOException {
        startServer("[{\"translations\":[]}]");

        Optional<String> result = new BingTranslatorAdapter(endpoint(), "key").translate("hello", "zh");

        assertTrue(result.isEmpty(), "translations 为空应返回 empty");
    }

    // 场景：译文文本为空白时返回 empty，避免回填无意义内容
    @Test
    @DisplayName("译文为空白时返回 empty")
    void blankTranslatedTextReturnsEmpty() throws IOException {
        startServer("[{\"translations\":[{\"text\":\"   \"}]}]");

        Optional<String> result = new BingTranslatorAdapter(endpoint(), "key").translate("hello", "zh");

        assertTrue(result.isEmpty(), "空白译文应返回 empty");
    }
}