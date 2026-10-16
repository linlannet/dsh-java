package net.linlan.dsh.infrastructure.adapter.mcp;

import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpMcpResourcePort MCP 资源 HTTP/SSE 传输")
class HttpMcpResourcePortTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("streamable-http 连接并列出资源")
    void streamableHttpListsResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("resources/list".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, resourcesResult()));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "streamable-http", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                Map.of(), 5000);
        port.connect();

        List<McpResource> resources = port.listResources(null);
        assertTrue(port.isConnected());
        assertEquals(2, resources.size());
        assertEquals("file:///a.txt", resources.get(0).uri());
        assertEquals("a.txt", resources.get(0).name());
        assertEquals("file:///b.txt", resources.get(1).uri());
        port.disconnect();
    }

    @Test
    @DisplayName("streamable-http 读取资源内容")
    void streamableHttpReadsResource() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("resources/read".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, resourceContentResult("hello world")));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "streamable-http", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                Map.of(), 5000);
        port.connect();

        String content = port.readResource("file:///a.txt");
        assertEquals("hello world", content);
        port.disconnect();
    }

    @Test
    @DisplayName("SSE 连接并列出资源")
    void sseListsResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write("event: endpoint\ndata: /messages\n\n".getBytes(StandardCharsets.UTF_8));
                body.flush();
            }
        });
        server.createContext("/messages", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("resources/list".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, resourcesResult()));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "sse", "http://127.0.0.1:" + server.getAddress().getPort() + "/sse",
                Map.of(), 5000);
        port.connect();

        List<McpResource> resources = port.listResources(null);
        assertEquals(2, resources.size());
        assertEquals("file:///a.txt", resources.get(0).uri());
        port.disconnect();
    }

    @Test
    @DisplayName("SSE 读取资源内容")
    void sseReadsResource() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write("event: endpoint\ndata: /messages\n\n".getBytes(StandardCharsets.UTF_8));
                body.flush();
            }
        });
        server.createContext("/messages", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("resources/read".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, resourceContentResult("sse content")));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "sse", "http://127.0.0.1:" + server.getAddress().getPort() + "/sse",
                Map.of(), 5000);
        port.connect();

        String content = port.readResource("file:///a.txt");
        assertEquals("sse content", content);
        port.disconnect();
    }

    @Test
    @DisplayName("SSE 202 Accepted 异步响应列出资源")
    void sseAcceptedAsyncListsResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<OutputStream> sseStream = new AtomicReference<>();
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream body = exchange.getResponseBody();
            sseStream.set(body);
            body.write("event: endpoint\ndata: /messages\n\n".getBytes(StandardCharsets.UTF_8));
            body.flush();
        });
        server.createContext("/messages", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            respondAccepted(exchange);
            ObjectNode result = "initialize".equals(request.path("method").asText())
                    ? objectMapper.createObjectNode()
                    : resourcesResult();
            synchronized (sseStream) {
                OutputStream body = sseStream.get();
                if (body != null) {
                    body.write(("event: message\ndata: " + objectMapper.writeValueAsString(
                            jsonRpcResponse(request, result)) + "\n\n").getBytes(StandardCharsets.UTF_8));
                    body.flush();
                }
            }
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "sse", "http://127.0.0.1:" + server.getAddress().getPort() + "/sse",
                Map.of(), 5000);
        port.connect();

        List<McpResource> resources = port.listResources(null);
        assertEquals(2, resources.size());
        port.disconnect();
    }

    @Test
    @DisplayName("未连接时列出资源抛异常")
    void listResourcesThrowsWhenNotConnected() {
        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "streamable-http", "http://127.0.0.1:9999/mcp", Map.of(), 1000);
        assertThrows(IllegalStateException.class, () -> port.listResources(null));
    }

    @Test
    @DisplayName("未连接时读取资源抛异常")
    void readResourceThrowsWhenNotConnected() {
        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "streamable-http", "http://127.0.0.1:9999/mcp", Map.of(), 1000);
        assertThrows(IllegalStateException.class, () -> port.readResource("file:///a"));
    }

    @Test
    @DisplayName("scope 过滤参数传递到请求")
    void scopeParamPassedToRequest() throws Exception {
        AtomicReference<String> receivedScope = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("resources/list".equals(method)) {
                receivedScope.set(request.path("params").path("scope").asText(""));
                respondJson(exchange, 200, jsonRpcResponse(request, resourcesResult()));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "streamable-http", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                Map.of(), 5000);
        port.connect();
        port.listResources("my-scope");
        assertEquals("my-scope", receivedScope.get());
        port.disconnect();
    }

    @Test
    @DisplayName("自定义请求头注入")
    void customHeadersInjected() throws Exception {
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            if ("initialize".equals(method)) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpResourcePort port = new HttpMcpResourcePort(
                "streamable-http", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                Map.of("Authorization", "Bearer token123"), 5000);
        port.connect();
        assertEquals("Bearer token123", receivedAuth.get());
        port.disconnect();
    }

    private ObjectNode resourcesResult() {
        ObjectNode res1 = objectMapper.createObjectNode();
        res1.put("uri", "file:///a.txt");
        res1.put("name", "a.txt");
        res1.put("description", "File A");
        ObjectNode res2 = objectMapper.createObjectNode();
        res2.put("uri", "file:///b.txt");
        res2.put("name", "b.txt");
        res2.put("description", "File B");
        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(res1);
        arr.add(res2);
        ObjectNode result = objectMapper.createObjectNode();
        result.set("resources", arr);
        return result;
    }

    private ObjectNode resourceContentResult(String text) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("uri", "file:///a.txt");
        content.put("text", text);
        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(content);
        ObjectNode result = objectMapper.createObjectNode();
        result.set("contents", arr);
        return result;
    }

    private ObjectNode jsonRpcResponse(JsonNode request, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", request.path("id"));
        response.set("result", result);
        return response;
    }

    private void respondJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    private void respondAccepted(HttpExchange exchange) throws IOException {
        byte[] bytes = "Accepted".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(202, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
