package net.linlan.dsh.infrastructure.adapter.mcp;

import net.linlan.dsh.domain.tool.mcp.IMcpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP HTTP client for streamable HTTP and legacy SSE transports.
 */
public class HttpMcpClient implements IMcpClient {

    private static final String TRANSPORT_SSE = "sse";
    private static final String TRANSPORT_STREAMABLE_HTTP = "streamable-http";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final String transport;
    private final URI endpoint;
    private final Map<String, String> headers;
    private final long callTimeoutMs;

    private volatile boolean connected;
    private volatile URI messageEndpoint;
    private volatile String sessionId;
    private volatile ExecutorService sseExecutor;

    public HttpMcpClient(String transport, String endpoint, Map<String, String> headers, long callTimeoutMs) {
        this.transport = TRANSPORT_SSE.equals(transport) ? TRANSPORT_SSE : TRANSPORT_STREAMABLE_HTTP;
        this.endpoint = URI.create(endpoint);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.callTimeoutMs = callTimeoutMs;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (TRANSPORT_SSE.equals(transport)) {
                    messageEndpoint = awaitSseEndpoint();
                } else {
                    messageEndpoint = endpoint;
                }

                JsonNode initializeResult = send("initialize", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "dsh-java", "version", "1.0.0")
                )).get(callTimeoutMs, TimeUnit.MILLISECONDS);

                sendNotification("notifications/initialized", Map.of());
                connected = true;
            } catch (Exception exception) {
                closeQuietly();
                throw new RuntimeException("Failed to connect to MCP server: " + exception.getMessage(), exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(this::closeQuietly);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public CompletableFuture<List<McpToolDescriptor>> listTools() {
        return send("tools/list", Map.of()).thenApply(result -> {
            List<McpToolDescriptor> tools = new ArrayList<>();
            JsonNode toolsNode = result.path("tools");
            if (toolsNode.isArray()) {
                toolsNode.forEach(toolNode -> tools.add(new McpToolDescriptor(
                        toolNode.path("name").asText(),
                        toolNode.path("description").asText(""),
                        objectMapper.convertValue(toolNode.path("inputSchema"), Map.class)
                )));
            }
            return tools;
        });
    }

    @Override
    public CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments) {
        return send("tools/call", Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments))
                .thenApply(result -> {
                    List<Map<String, Object>> content = new ArrayList<>();
                    JsonNode contentNode = result.path("content");
                    if (contentNode.isArray()) {
                        contentNode.forEach(item -> content.add(objectMapper.convertValue(item, Map.class)));
                    }
                    return new McpToolResult(content, result.path("isError").asBoolean(false));
                });
    }

    private URI awaitSseEndpoint() {
        CompletableFuture<URI> endpointFuture = new CompletableFuture<>();
        sseExecutor = Executors.newSingleThreadExecutor(thread -> {
            Thread threadObject = new Thread(thread, "mcp-sse-reader-" + endpoint.getHost());
            threadObject.setDaemon(true);
            return threadObject;
        });
        sseExecutor.execute(() -> readSse(endpointFuture));
        try {
            return endpointFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new RuntimeException("Timed out waiting for MCP SSE endpoint event", exception);
        }
    }

    private void readSse(CompletableFuture<URI> endpointFuture) {
        try {
            HttpRequest request = requestBuilder(endpoint, "GET")
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("SSE connect failed with HTTP " + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String event = "message";
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        handleSseEvent(endpointFuture, event, data.toString());
                        event = "message";
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        event = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append('\n');
                        data.append(line.substring("data:".length()).trim());
                    }
                }
            }
            failPending("MCP SSE stream closed");
        } catch (Exception exception) {
            endpointFuture.completeExceptionally(exception);
            failPending(exception.getMessage());
        }
    }

    private void handleSseEvent(CompletableFuture<URI> endpointFuture, String event, String data) {
        if (data == null || data.isBlank()) return;
        if ("endpoint".equalsIgnoreCase(event)) {
            endpointFuture.complete(endpoint.resolve(data.trim()));
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            if (node.has("id")) {
                complete(node);
            }
        } catch (IOException ignored) {
            // Ignore heartbeat or non-JSON SSE frames.
        }
    }

    private CompletableFuture<JsonNode> send(String method, Map<String, Object> params) {
        long id = nextId.getAndIncrement();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", objectMapper.valueToTree(params));

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            URI target = Optional.ofNullable(messageEndpoint).orElse(endpoint);
            HttpRequest requestObject = requestBuilder(target, "POST")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(requestObject, HttpResponse.BodyHandlers.ofString());
            handleResponse(response, id);
        } catch (Exception exception) {
            pending.remove(id);
            future.completeExceptionally(new RuntimeException("Failed to send MCP request: " + exception.getMessage(), exception));
        }
        return future.orTimeout(callTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void sendNotification(String method, Map<String, Object> params) {
        try {
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            notification.set("params", objectMapper.valueToTree(params));
            URI target = Optional.ofNullable(messageEndpoint).orElse(endpoint);
            HttpRequest request = requestBuilder(target, "POST")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(notification)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception exception) {
            throw new RuntimeException("Failed to send MCP notification: " + exception.getMessage(), exception);
        }
    }

    private void handleResponse(HttpResponse<String> response, long id) {
        CompletableFuture<JsonNode> future = pending.get(id);
        if (future == null) return;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            pending.remove(id);
            future.completeExceptionally(new RuntimeException("MCP HTTP request failed: HTTP " + response.statusCode()
                    + " " + response.body()));
            return;
        }
        response.headers().firstValue("Mcp-Session-Id").ifPresent(value -> sessionId = value);
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        if (contentType.contains("text/event-stream")) {
            parseSseBody(response.body());
            return;
        }
        if (TRANSPORT_SSE.equals(transport) && !contentType.contains("application/json")) {
            // Legacy SSE servers acknowledge POST requests with 202 Accepted and deliver the
            // JSON-RPC response asynchronously on the GET /sse stream.
            return;
        }
        if (response.body() == null || response.body().isBlank()) {
            return;
        }
        try {
            complete(objectMapper.readTree(response.body()));
        } catch (IOException exception) {
            pending.remove(id);
            future.completeExceptionally(new RuntimeException("Invalid MCP JSON response", exception));
        }
    }

    private void parseSseBody(String body) {
        String event = "message";
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.isBlank()) {
                handleSseEvent(new CompletableFuture<>(), event, data.toString());
                event = "message";
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) event = line.substring("event:".length()).trim();
            else if (line.startsWith("data:")) {
                if (data.length() > 0) data.append('\n');
                data.append(line.substring("data:".length()).trim());
            }
        }
        handleSseEvent(new CompletableFuture<>(), event, data.toString());
    }

    private void complete(JsonNode node) {
        long id = node.path("id").asLong(-1);
        if (id < 0) return;
        CompletableFuture<JsonNode> future = pending.remove(id);
        if (future == null) return;
        if (node.has("error")) {
            future.completeExceptionally(new RuntimeException("MCP error " + node.path("error").path("code").asInt()
                    + ": " + node.path("error").path("message").asText()));
            return;
        }
        future.complete(node.path("result"));
    }

    private HttpRequest.Builder requestBuilder(URI target, String method) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(callTimeoutMs));
        headers.forEach(builder::header);
        if (sessionId != null && !sessionId.isBlank() && !"GET".equals(method)) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return builder;
    }

    private void failPending(String message) {
        pending.values().forEach(future -> future.completeExceptionally(new RuntimeException(message)));
        pending.clear();
    }

    private void closeQuietly() {
        connected = false;
        failPending("Disconnected");
        if (sseExecutor != null) {
            sseExecutor.shutdownNow();
            sseExecutor = null;
        }
    }
}
