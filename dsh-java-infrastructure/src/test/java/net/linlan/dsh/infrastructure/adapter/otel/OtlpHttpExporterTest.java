package net.linlan.dsh.infrastructure.adapter.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
}
