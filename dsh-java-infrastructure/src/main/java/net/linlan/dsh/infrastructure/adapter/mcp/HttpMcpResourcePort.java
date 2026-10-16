package net.linlan.dsh.infrastructure.adapter.mcp;

import net.linlan.dsh.domain.tool.mcp.resource.adapter.port.IMcpResourcePort;
import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
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
 * HttpMcpResourcePort 表示通过 HTTP/SSE JSON-RPC 实现 MCP 资源端口的基础设施适配器。
 */
public class HttpMcpResourcePort implements IMcpResourcePort {

    private static final String TRANSPORT_SSE = "sse";
    private static final String TRANSPORT_STREAMABLE_HTTP = "streamable-http";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final String transport;
    private final URI endpoint;
    private final Map<String, String> headers;
    private final long callTimeoutMs;
    private final HttpClient httpClient;

    private volatile boolean connected;
    private volatile URI messageEndpoint;
    private volatile String sessionId;
    private volatile ExecutorService sseExecutor;

    public HttpMcpResourcePort(String transport, String endpoint, Map<String, String> headers, long callTimeoutMs) {
        this(transport, endpoint, headers, callTimeoutMs, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public HttpMcpResourcePort(String transport, String endpoint, Map<String, String> headers, long callTimeoutMs,
                               HttpClient httpClient) {
        this.transport = TRANSPORT_SSE.equals(transport) ? TRANSPORT_SSE : TRANSPORT_STREAMABLE_HTTP;
        this.endpoint = URI.create(endpoint);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.callTimeoutMs = callTimeoutMs;
        this.httpClient = httpClient;
    }

    @Override
    public List<McpResource> listResources(String scope) {
        ensureConnected();
        try {
            ObjectNode params = mapper.createObjectNode();
            if (scope != null && !scope.isBlank()) {
                params.put("scope", scope);
            }
            JsonNode result = send("resources/list", params);
            List<McpResource> resources = new ArrayList<>();
            JsonNode arr = result.path("resources");
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    resources.add(new McpResource(
                            node.path("uri").asText(""),
                            node.path("name").asText(""),
                            node.path("description").asText(""),
                            node.path("scope").asText(scope != null ? scope : "")
                    ));
                }
            }
            return resources;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public String readResource(String uri) {
        ensureConnected();
        try {
            ObjectNode params = mapper.createObjectNode();
            params.put("uri", uri);
            JsonNode result = send("resources/read", params);
            JsonNode contents = result.path("contents");
            if (contents.isArray() && !contents.isEmpty()) {
                return contents.get(0).path("text").asText("");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public void connect() throws Exception {
        if (TRANSPORT_SSE.equals(transport)) {
            messageEndpoint = awaitSseEndpoint();
        } else {
            messageEndpoint = endpoint;
        }
        send("initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "dsh-java", "version", "1.0.0")
        ));
        sendNotification("notifications/initialized", Map.of());
        connected = true;
    }

    public void disconnect() {
        connected = false;
        failPending("Disconnected");
        if (sseExecutor != null) {
            sseExecutor.shutdownNow();
            sseExecutor = null;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException("MCP resource port not connected");
        }
    }

    private JsonNode send(String method, Object params) throws Exception {
        long id = nextId.getAndIncrement();
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", mapper.valueToTree(params));

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        URI target = Optional.ofNullable(messageEndpoint).orElse(endpoint);
        HttpRequest.Builder builder = requestBuilder(target)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)));
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        handleResponse(response, id);
        return future.get(callTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void sendNotification(String method, Object params) throws Exception {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", mapper.valueToTree(params));
        URI target = Optional.ofNullable(messageEndpoint).orElse(endpoint);
        HttpRequest request = requestBuilder(target)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(notification)))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private URI awaitSseEndpoint() {
        CompletableFuture<URI> endpointFuture = new CompletableFuture<>();
        sseExecutor = Executors.newSingleThreadExecutor(thread -> {
            Thread t = new Thread(thread, "mcp-sse-resource-reader-" + endpoint.getHost());
            t.setDaemon(true);
            return t;
        });
        sseExecutor.execute(() -> readSse(endpointFuture));
        try {
            return endpointFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Timed out waiting for MCP SSE endpoint event", e);
        }
    }

    private void readSse(CompletableFuture<URI> endpointFuture) {
        try {
            HttpRequest request = requestBuilder(endpoint)
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
        } catch (Exception e) {
            endpointFuture.completeExceptionally(e);
            failPending(e.getMessage());
        }
    }

    private void handleSseEvent(CompletableFuture<URI> endpointFuture, String event, String data) {
        if (data == null || data.isBlank()) return;
        if ("endpoint".equalsIgnoreCase(event)) {
            endpointFuture.complete(endpoint.resolve(data.trim()));
            return;
        }
        try {
            JsonNode node = mapper.readTree(data);
            if (node.has("id")) {
                complete(node);
            }
        } catch (IOException ignored) {
        }
    }

    private void handleResponse(HttpResponse<String> response, long id) {
        CompletableFuture<JsonNode> future = pending.get(id);
        if (future == null) return;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            pending.remove(id);
            future.completeExceptionally(new RuntimeException("MCP HTTP request failed: HTTP " + response.statusCode()));
            return;
        }
        response.headers().firstValue("Mcp-Session-Id").ifPresent(value -> sessionId = value);
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        if (contentType.contains("text/event-stream")) {
            parseSseBody(response.body());
            return;
        }
        if (TRANSPORT_SSE.equals(transport) && !contentType.contains("application/json")) {
            return;
        }
        if (response.body() == null || response.body().isBlank()) {
            return;
        }
        try {
            complete(mapper.readTree(response.body()));
        } catch (IOException e) {
            pending.remove(id);
            future.completeExceptionally(new RuntimeException("Invalid MCP JSON response", e));
        }
    }

    private void parseSseBody(String body) {
        String event = "message";
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.isBlank()) {
                handleSseEvent(null, event, data.toString());
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
        handleSseEvent(null, event, data.toString());
    }

    private void complete(JsonNode node) {
        long id = node.path("id").asLong(-1);
        if (id < 0) return;
        CompletableFuture<JsonNode> future = pending.remove(id);
        if (future == null) return;
        if (node.has("error")) {
            future.completeExceptionally(new RuntimeException("MCP error: " + node.path("error").path("message").asText()));
            return;
        }
        future.complete(node.path("result"));
    }

    private HttpRequest.Builder requestBuilder(URI target) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(callTimeoutMs));
        headers.forEach(builder::header);
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return builder;
    }

    private void failPending(String message) {
        pending.values().forEach(future -> future.completeExceptionally(new RuntimeException(message)));
        pending.clear();
    }
}
