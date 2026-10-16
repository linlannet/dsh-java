package net.linlan.dsh.infrastructure.adapter.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.022 OtlpHttpExporter OTLP HTTP 导出")
class OtlpHttpExporterTest {

    @Test
    @DisplayName("单参构造函数设置默认 endpoint")
    void singleArgConstructorSetsEndpoint() {
        OtlpHttpExporter exporter = new OtlpHttpExporter("http://127.0.0.1:4318/v1/traces");
        assertEquals("http://127.0.0.1:4318/v1/traces", exporter.endpoint());
    }

    @Test
    @DisplayName("双参构造函数注入 HttpClient 并设置 endpoint")
    void twoArgConstructorInjectsClient() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        OtlpHttpExporter exporter = new OtlpHttpExporter("http://example/v1/traces", client);
        assertEquals("http://example/v1/traces", exporter.endpoint());
    }

    @Test
    @DisplayName("export 发送 POST 请求体与 Content-Type 头到 endpoint")
    void exportPostsBodyAndContentTypeHeader() throws IOException {
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedBody.set(exchange.getRequestBody().readAllBytes());
                receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint);
            byte[] payload = "otlp-payload".getBytes(StandardCharsets.UTF_8);
            assertDoesNotThrow(() -> exporter.export(payload));
        } finally {
            server.stop(0);
        }
        assertEquals("otlp-payload", new String(receivedBody.get(), StandardCharsets.UTF_8));
        assertEquals("application/x-protobuf", receivedContentType.get());
    }

    @Test
    @DisplayName("export 对 4xx 响应不抛异常")
    void exportDoesNotThrowOn4xxResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint);
            assertDoesNotThrow(() -> exporter.export(new byte[] {1, 2, 3}));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("export 对不可达 endpoint 不抛异常")
    void exportDoesNotThrowOnUnreachableEndpoint() {
        OtlpHttpExporter exporter = new OtlpHttpExporter("http://127.0.0.1:1/v1/traces");
        assertDoesNotThrow(() -> exporter.export(new byte[] {1}));
    }

    @Test
    @DisplayName("export 空字节数组不抛异常")
    void exportEmptyByteArrayDoesNotThrow() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces").toString();
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint);
            assertDoesNotThrow(() -> exporter.export(new byte[0]));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("sampleRate=0.0 时跳过导出不发送请求")
    void sampleRateZeroSkipsExport() throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 0.0, 0, Duration.ZERO, null, null);
            exporter.export(new byte[] {1, 2, 3});
            assertEquals(0, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("sampleRate=1.0 时始终导出")
    void sampleRateOneAlwaysExports() throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 0, Duration.ZERO, null, null);
            exporter.export(new byte[] {1});
            exporter.export(new byte[] {2});
            exporter.export(new byte[] {3});
            assertEquals(3, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("5xx 响应触发重试并最终成功")
    void retryOn5xxThenSucceeds() throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                int n = requestCount.incrementAndGet();
                if (n < 3) {
                    exchange.sendResponseHeaders(500, -1);
                } else {
                    exchange.sendResponseHeaders(200, -1);
                }
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 3, Duration.ofMillis(1), null, null);
            assertDoesNotThrow(() -> exporter.export(new byte[] {1}));
            assertEquals(3, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("4xx 响应不触发重试")
    void noRetryOn4xx() throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 3, Duration.ofMillis(1), null, null);
            assertDoesNotThrow(() -> exporter.export(new byte[] {1}));
            assertEquals(1, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("鉴权头注入到请求中")
    void authHeaderInjectedIntoRequest() throws IOException {
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 0, Duration.ZERO,
                    "Authorization", "Bearer my-secret-token");
            exporter.export(new byte[] {1});
            assertEquals("Bearer my-secret-token", receivedAuth.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("自定义鉴权头名与值注入到请求中")
    void customAuthHeaderInjected() throws IOException {
        AtomicReference<String> receivedHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 0, Duration.ZERO,
                    "X-API-Key", "abc123");
            exporter.export(new byte[] {1});
            assertEquals("abc123", receivedHeader.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("无鉴权配置时不发送鉴权头")
    void noAuthHeaderWhenNotConfigured() throws IOException {
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 0, Duration.ZERO, null, null);
            exporter.export(new byte[] {1});
            assertNull(receivedAuth.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("5xx 重试耗尽后不抛异常")
    void retryExhaustedDoesNotThrow() throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces";
            OtlpHttpExporter exporter = new OtlpHttpExporter(endpoint, 1.0, 2, Duration.ofMillis(1), null, null);
            assertDoesNotThrow(() -> exporter.export(new byte[] {1}));
            assertEquals(3, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("配置参数 getter 返回正确值")
    void configGettersReturnCorrectValues() {
        OtlpHttpExporter exporter = new OtlpHttpExporter("http://example/v1/traces", 0.5, 5,
                Duration.ofMillis(200), "Authorization", "Bearer token");
        assertEquals("http://example/v1/traces", exporter.endpoint());
        assertEquals(0.5, exporter.sampleRate());
        assertEquals(5, exporter.maxRetries());
        assertEquals("Authorization", exporter.authHeader());
    }

    @Test
    @DisplayName("sampleRate 超出范围被截断到 [0,1]")
    void sampleRateClampedToRange() {
        OtlpHttpExporter high = new OtlpHttpExporter("http://example/v1/traces", 2.0, 0, Duration.ZERO, null, null);
        assertEquals(1.0, high.sampleRate());
        OtlpHttpExporter low = new OtlpHttpExporter("http://example/v1/traces", -0.5, 0, Duration.ZERO, null, null);
        assertEquals(0.0, low.sampleRate());
    }

    @Test
    @DisplayName("空白鉴权头被视为未配置")
    void blankAuthHeaderTreatedAsNotConfigured() {
        OtlpHttpExporter exporter = new OtlpHttpExporter("http://example/v1/traces", 1.0, 0, Duration.ZERO,
                "  ", "token");
        assertNull(exporter.authHeader());
    }
}
