package net.linlan.dsh.infrastructure.adapter.speech;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SenseVoiceSpeechAdapterTest SenseVoice 语音转写适配器单元测试。
 *
 * <p>不访问真实外部网络：成功/失败响应路径由 127.0.0.1 上的进程内 HttpServer 提供，
 * 不可达路径由保留的本地端口 1 触发，其余为纯入参校验。</p>
 */
@DisplayName("SenseVoiceSpeechAdapter 语音 Provider 适配器")
public class SenseVoiceSpeechAdapterTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    /** 启动一个仅在回环地址监听的固定响应服务器，并记录收到的 Authorization 头。 */
    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/transcribe", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            if (body == null || body.isEmpty()) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, payload.length);
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
        return "http://127.0.0.1:" + port + "/transcribe";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // 场景：Provider 名称固定为 sensevoice
    @Test
    @DisplayName("name 恒为 sensevoice")
    void nameIsSensevoice() {
        assertEquals("sensevoice", new SenseVoiceSpeechAdapter("http://127.0.0.1:1/transcribe").name());
    }

    // 场景：endpoint 为空白时判定 Provider 不可用，转写直接返回 empty
    @Test
    @DisplayName("空白 endpoint 不可用且转写返回 empty")
    void blankEndpointIsUnavailable() {
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter("   ");

        assertFalse(adapter.available(), "空白 endpoint 应判定为不可用");
        assertTrue(adapter.transcribe(new byte[]{1, 2, 3}).isEmpty(), "不可用时转写应返回 empty");
    }

    // 场景：endpoint 为 null 时归一化为空串，Provider 不可用
    @Test
    @DisplayName("null endpoint 归一化为不可用")
    void nullEndpointIsUnavailable() {
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter(null);

        assertFalse(adapter.available(), "null endpoint 应判定为不可用");
        assertTrue(adapter.transcribe(new byte[]{9}).isEmpty(), "不可用时转写应返回 empty");
    }

    // 场景：音频数据为 null 时即使 Provider 可用也不发起请求，返回 empty
    @Test
    @DisplayName("音频为 null 时返回 empty")
    void nullAudioReturnsEmpty() {
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter("http://127.0.0.1:1/transcribe", "key");

        assertTrue(adapter.available(), "非空 endpoint 应可用");
        assertTrue(adapter.transcribe(null).isEmpty(), "null 音频应返回 empty");
    }

    // 场景：音频数据为空数组时不发起请求，返回 empty
    @Test
    @DisplayName("音频为空数组时返回 empty")
    void emptyAudioReturnsEmpty() {
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter("http://127.0.0.1:1/transcribe");

        assertTrue(adapter.transcribe(new byte[0]).isEmpty(), "空音频应返回 empty");
    }

    // 场景：endpoint 不可达时降级返回 empty 且不抛出异常
    @Test
    @DisplayName("不可达 endpoint 降级返回 empty")
    void unreachableEndpointReturnsEmpty() {
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter("http://127.0.0.1:1/transcribe", "key");

        Optional<String> result = assertDoesNotThrow(() -> adapter.transcribe(new byte[]{1, 2, 3}),
                "网络失败应被捕获而非抛出");
        assertTrue(result.isEmpty(), "不可达时应返回 empty");
    }

    // 场景：服务端 2xx 且返回非空文本时，应返回去除首尾空白的转写结果，并携带鉴权头
    @Test
    @DisplayName("2xx 非空响应返回去空白文本并携带鉴权头")
    void successReturnsTrimmedBodyWithAuthHeader() throws IOException {
        startServer(200, "  hello-world  ");
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter(endpoint(), "k-123");

        Optional<String> result = adapter.transcribe(new byte[]{1, 2, 3});

        assertTrue(result.isPresent(), "2xx 非空响应应返回结果");
        assertEquals("hello-world", result.get(), "应返回去除首尾空白后的文本");
        assertEquals("Bearer k-123", lastAuthorization.get(), "应携带 Bearer 鉴权头");
    }

    // 场景：未配置 apiKey 时不发送 Authorization 头
    @Test
    @DisplayName("无 apiKey 时不发送鉴权头")
    void missingApiKeySendsNoAuthHeader() throws IOException {
        startServer(200, "ok");
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter(endpoint());

        Optional<String> result = adapter.transcribe(new byte[]{7});

        assertTrue(result.isPresent(), "2xx 非空响应应返回结果");
        assertEquals("ok", result.get(), "应返回响应文本");
        assertNull(lastAuthorization.get(), "未配置 apiKey 时不应发送 Authorization 头");
    }

    // 场景：服务端 2xx 但响应体为空时返回 empty
    @Test
    @DisplayName("2xx 空响应体返回 empty")
    void blankBodyReturnsEmpty() throws IOException {
        startServer(200, "");

        Optional<String> result = new SenseVoiceSpeechAdapter(endpoint()).transcribe(new byte[]{1});

        assertTrue(result.isEmpty(), "空响应体应返回 empty");
    }

    // 场景：服务端非 2xx 时返回 empty（不解析错误体）
    @Test
    @DisplayName("非 2xx 响应返回 empty")
    void errorStatusReturnsEmpty() throws IOException {
        startServer(500, "server-error");

        Optional<String> result = new SenseVoiceSpeechAdapter(endpoint(), "k").transcribe(new byte[]{1});

        assertTrue(result.isEmpty(), "非 2xx 应返回 empty");
    }

    // 场景：apiKey 为空白字符串（非 null）时应视为未配置，不发送 Authorization 头
    @Test
    @DisplayName("空白 apiKey 不发送鉴权头")
    void blankApiKeySendsNoAuthHeader() throws IOException {
        startServer(200, "ok");
        SenseVoiceSpeechAdapter adapter = new SenseVoiceSpeechAdapter(endpoint(), "   ");

        Optional<String> result = adapter.transcribe(new byte[]{7});

        assertTrue(result.isPresent(), "2xx 非空响应应返回结果");
        assertEquals("ok", result.get(), "应返回响应文本");
        assertNull(lastAuthorization.get(), "空白 apiKey 不应发送 Authorization 头");
    }
}