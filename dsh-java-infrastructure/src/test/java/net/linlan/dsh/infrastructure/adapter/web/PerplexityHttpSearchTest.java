package net.linlan.dsh.infrastructure.adapter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.linlan.dsh.domain.tool.web.search.model.valobj.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.032 PerplexityHttpSearch Perplexity API 基础设施适配器")
class PerplexityHttpSearchTest {

    @Test
    @DisplayName("单参构造函数使用默认 endpoint")
    void singleArgConstructorDoesNotThrow() {
        PerplexityHttpSearch search = new PerplexityHttpSearch("test-key");
        assertTrue(search != null);
    }

    @Test
    @DisplayName("双参构造函数接受自定义 endpoint")
    void twoArgConstructorAcceptsCustomEndpoint() {
        PerplexityHttpSearch search = new PerplexityHttpSearch("http://localhost:9999/chat", "key");
        assertTrue(search != null);
    }

    @Test
    @DisplayName("search null/blank 查询返回空列表")
    void searchNullOrBlankQueryReturnsEmpty() {
        PerplexityHttpSearch search = new PerplexityHttpSearch("key");
        assertEquals(List.of(), search.search(null, 5));
        assertEquals(List.of(), search.search("", 5));
        assertEquals(List.of(), search.search("   ", 5));
    }

    @Test
    @DisplayName("search 构建请求体并发送 Bearer Authorization 头，解析 citations")
    void searchBuildsRequestBodyAndParsesCitations() throws IOException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        String responseJson = "{\"citations\":[{\"url\":\"https://u1\",\"title\":\"T1\",\"snippet\":\"S1\"}]}";
        HttpServer server = startCapturingServer(receivedBody, receivedAuth, responseJson, 200);
        try {
            PerplexityHttpSearch search = new PerplexityHttpSearch(endpoint(server), "my-key");
            List<SearchResult> results = search.search("my-query", 4);
            assertEquals(1, results.size());
            assertEquals("T1", results.get(0).title());
            assertEquals("https://u1", results.get(0).url());
            assertEquals("S1", results.get(0).snippet());
        } finally {
            server.stop(0);
        }
        String body = receivedBody.get();
        assertTrue(body.contains("\"model\":\"sonar\""));
        assertTrue(body.contains("\"content\":\"my-query\""));
        assertTrue(body.contains("\"max_results\":4"));
        assertEquals("Bearer my-key", receivedAuth.get());
    }

    @Test
    @DisplayName("search citations 为空时回退到 choices.message.content")
    void searchFallsBackToChoicesContentWhenNoCitations() throws IOException {
        String responseJson = "{\"choices\":[{\"message\":{\"content\":\"perplexity-answer\"}}]}";
        HttpServer server = startCapturingServer(new AtomicReference<>(), new AtomicReference<>(), responseJson, 200);
        try {
            PerplexityHttpSearch search = new PerplexityHttpSearch(endpoint(server), "key");
            List<SearchResult> results = search.search("q", 2);
            assertEquals(1, results.size());
            assertEquals("Perplexity Answer", results.get(0).title());
            assertEquals("", results.get(0).url());
            assertEquals("perplexity-answer", results.get(0).snippet());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("search 解析多个 citations")
    void searchParsesMultipleCitations() throws IOException {
        String responseJson = "{\"citations\":[" +
                "{\"url\":\"ua\",\"title\":\"A\",\"snippet\":\"sa\"}," +
                "{\"url\":\"ub\",\"title\":\"B\",\"snippet\":\"sb\"}" +
                "]}";
        HttpServer server = startCapturingServer(new AtomicReference<>(), new AtomicReference<>(), responseJson, 200);
        try {
            PerplexityHttpSearch search = new PerplexityHttpSearch(endpoint(server), "key");
            List<SearchResult> results = search.search("q", 2);
            assertEquals(2, results.size());
            assertEquals("A", results.get(0).title());
            assertEquals("B", results.get(1).title());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("search 对 5xx 响应返回空列表")
    void searchReturnsEmptyOnServerError() throws IOException {
        HttpServer server = startCapturingServer(new AtomicReference<>(), new AtomicReference<>(), "", 500);
        try {
            PerplexityHttpSearch search = new PerplexityHttpSearch(endpoint(server), "key");
            List<SearchResult> results = search.search("q", 2);
            assertTrue(results.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("search 对不可达 endpoint 返回空列表")
    void searchReturnsEmptyOnUnreachableEndpoint() {
        PerplexityHttpSearch search = new PerplexityHttpSearch("http://127.0.0.1:1/chat", "key");
        List<SearchResult> results = search.search("q", 2);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("PerplexityHttpSearch 实现 IWebSearchPort")
    void implementsIWebSearchPort() {
        PerplexityHttpSearch search = new PerplexityHttpSearch("key");
        assertTrue(search instanceof net.linlan.dsh.domain.tool.web.search.adapter.port.IWebSearchPort);
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
    }

    private static HttpServer startCapturingServer(AtomicReference<String> bodyRef,
                                                    AtomicReference<String> authRef,
                                                    String responseJson,
                                                    int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] resp = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, resp.length);
                exchange.getResponseBody().write(resp);
                exchange.getResponseBody().close();
            }
        });
        server.start();
        return server;
    }
}
