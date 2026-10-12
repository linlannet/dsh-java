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

@DisplayName("T2.032 ExaHttpSearch Exa API 基础设施适配器")
class ExaHttpSearchTest {

    @Test
    @DisplayName("单参构造函数使用默认 endpoint")
    void singleArgConstructorDoesNotThrow() {
        ExaHttpSearch search = new ExaHttpSearch("test-key");
        assertTrue(search != null);
    }

    @Test
    @DisplayName("双参构造函数接受自定义 endpoint")
    void twoArgConstructorAcceptsCustomEndpoint() {
        ExaHttpSearch search = new ExaHttpSearch("http://localhost:9999/search", "key");
        assertTrue(search != null);
    }

    @Test
    @DisplayName("search null 查询返回空列表")
    void searchNullQueryReturnsEmpty() {
        ExaHttpSearch search = new ExaHttpSearch("key");
        assertEquals(List.of(), search.search(null, 5));
    }

    @Test
    @DisplayName("search 空/blank 查询返回空列表")
    void searchBlankQueryReturnsEmpty() {
        ExaHttpSearch search = new ExaHttpSearch("key");
        assertEquals(List.of(), search.search("", 5));
        assertEquals(List.of(), search.search("   ", 5));
    }

    @Test
    @DisplayName("search 构建请求体并发送 x-api-key 头，解析响应")
    void searchBuildsRequestBodyAndParsesResponse() throws IOException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedApiKey = new AtomicReference<>();
        String responseJson = "{\"results\":[{\"title\":\"T1\",\"url\":\"https://u1\",\"text\":\"S1\"}]}";
        HttpServer server = startCapturingServer(receivedBody, receivedApiKey, responseJson, 200);
        try {
            ExaHttpSearch search = new ExaHttpSearch(endpoint(server), "my-api-key");
            List<SearchResult> results = search.search("my-query", 3);
            assertEquals(1, results.size());
            assertEquals("T1", results.get(0).title());
            assertEquals("https://u1", results.get(0).url());
            assertEquals("S1", results.get(0).snippet());
        } finally {
            server.stop(0);
        }
        String body = receivedBody.get();
        assertTrue(body.contains("\"query\":\"my-query\""));
        assertTrue(body.contains("\"numResults\":3"));
        assertTrue(body.contains("\"contents\""));
        assertEquals("my-api-key", receivedApiKey.get());
    }

    @Test
    @DisplayName("search 对 5xx 响应返回空列表")
    void searchReturnsEmptyOnServerError() throws IOException {
        HttpServer server = startCapturingServer(new AtomicReference<>(), new AtomicReference<>(), "", 500);
        try {
            ExaHttpSearch search = new ExaHttpSearch(endpoint(server), "key");
            List<SearchResult> results = search.search("q", 2);
            assertTrue(results.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("search 对不可达 endpoint 返回空列表")
    void searchReturnsEmptyOnUnreachableEndpoint() {
        ExaHttpSearch search = new ExaHttpSearch("http://127.0.0.1:1/search", "key");
        List<SearchResult> results = search.search("q", 2);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search snippet 超过 200 字符被截断")
    void searchTruncatesSnippetOver200Chars() throws IOException {
        String longText = "x".repeat(250);
        String responseJson = "{\"results\":[{\"title\":\"T\",\"url\":\"U\",\"text\":\"" + longText + "\"}]}";
        HttpServer server = startCapturingServer(new AtomicReference<>(), new AtomicReference<>(), responseJson, 200);
        try {
            ExaHttpSearch search = new ExaHttpSearch(endpoint(server), "key");
            List<SearchResult> results = search.search("q", 1);
            assertEquals(1, results.size());
            assertEquals(200, results.get(0).snippet().length());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("search 解析多结果响应")
    void searchParsesMultipleResults() throws IOException {
        String responseJson = "{\"results\":[" +
                "{\"title\":\"A\",\"url\":\"ua\",\"text\":\"sa\"}," +
                "{\"title\":\"B\",\"url\":\"ub\",\"text\":\"sb\"}" +
                "]}";
        HttpServer server = startCapturingServer(new AtomicReference<>(), new AtomicReference<>(), responseJson, 200);
        try {
            ExaHttpSearch search = new ExaHttpSearch(endpoint(server), "key");
            List<SearchResult> results = search.search("q", 2);
            assertEquals(2, results.size());
            assertEquals("A", results.get(0).title());
            assertEquals("B", results.get(1).title());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("ExaHttpSearch 实现 IWebSearchPort")
    void implementsIWebSearchPort() {
        ExaHttpSearch search = new ExaHttpSearch("key");
        assertTrue(search instanceof net.linlan.dsh.domain.tool.web.search.adapter.port.IWebSearchPort);
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
    }

    private static HttpServer startCapturingServer(AtomicReference<String> bodyRef,
                                                    AtomicReference<String> apiKeyRef,
                                                    String responseJson,
                                                    int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                apiKeyRef.set(exchange.getRequestHeaders().getFirst("x-api-key"));
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
