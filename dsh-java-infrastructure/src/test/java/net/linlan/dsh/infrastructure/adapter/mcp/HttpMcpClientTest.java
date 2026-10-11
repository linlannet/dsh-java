package net.linlan.dsh.infrastructure.adapter.mcp;

import net.linlan.dsh.domain.tool.mcp.IMcpClient;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpMcpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void connectsAndListsToolsOverStreamableHttp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            if ("initialize".equals(request.path("method").asText())) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("tools/list".equals(request.path("method").asText())) {
                respondJson(exchange, 200, jsonRpcResponse(request, toolsResult()));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpClient client = new HttpMcpClient(
                "streamable-http", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                Map.of(), 5000);
        client.connect().get(5, TimeUnit.SECONDS);

        List<IMcpClient.McpToolDescriptor> tools = client.listTools().get(5, TimeUnit.SECONDS);

        assertTrue(client.isConnected());
        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).name());
    }

    @Test
    void connectsAndListsToolsOverLegacySse() throws Exception {
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
            if ("initialize".equals(request.path("method").asText())) {
                respondJson(exchange, 200, jsonRpcResponse(request, objectMapper.createObjectNode()));
                return;
            }
            if ("tools/list".equals(request.path("method").asText())) {
                respondJson(exchange, 200, jsonRpcResponse(request, toolsResult()));
                return;
            }
            exchange.sendResponseHeaders(202, -1);
        });
        server.start();

        HttpMcpClient client = new HttpMcpClient(
                "sse", "http://127.0.0.1:" + server.getAddress().getPort() + "/sse",
                Map.of(), 5000);
        client.connect().get(5, TimeUnit.SECONDS);

        List<IMcpClient.McpToolDescriptor> tools = client.listTools().get(5, TimeUnit.SECONDS);

        assertTrue(client.isConnected());
        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).name());
    }

    @Test
    void connectsAndListsToolsWhenLegacySsePostIsAccepted() throws Exception {
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
                    : toolsResult();
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

        HttpMcpClient client = new HttpMcpClient(
                "sse", "http://127.0.0.1:" + server.getAddress().getPort() + "/sse",
                Map.of(), 5000);
        client.connect().get(5, TimeUnit.SECONDS);

        List<IMcpClient.McpToolDescriptor> tools = client.listTools().get(5, TimeUnit.SECONDS);

        assertTrue(client.isConnected());
        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).name());
    }

    private ObjectNode toolsResult() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "search");
        tool.put("description", "Search the web");
        tool.set("inputSchema", objectMapper.createObjectNode());
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(tool);
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
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
