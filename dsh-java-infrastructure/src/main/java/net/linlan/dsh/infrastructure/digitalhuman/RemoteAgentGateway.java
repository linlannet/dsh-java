package net.linlan.dsh.infrastructure.digitalhuman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteAgentGateway {
    private static final Logger log = LoggerFactory.getLogger(RemoteAgentGateway.class);
    private static final long STREAM_IDLE_CLOSE_MS = 300000L;
    private static final long APPROVAL_IDLE_CLOSE_MS = 700000L;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public RemoteAgentGateway() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).build();
        this.objectMapper = new ObjectMapper();
    }

    public void runRemote(String baseUrl, String token, String instruction, String channelCode, String approvalMode, EventSink sink) throws Exception {
        this.runRemote(null, baseUrl, token, instruction, channelCode, approvalMode, sink);
    }

    public void runRemote(String taskKey, String baseUrl, String token, String instruction, String channelCode, String approvalMode, EventSink sink) throws Exception {
        String url = baseUrl.replaceAll("/+$", "") + "/api/agent/stream";
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(10L)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(this.buildBody(instruction, null, approvalMode, null, null)));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        this.stream(builder.build(), sink, taskKey);
    }

    public void runA2a(String baseUrl, String token, String instruction, EventSink sink) throws Exception {
        this.runA2a(null, baseUrl, token, instruction, sink);
    }

    public void runA2a(String taskKey, String baseUrl, String token, String instruction, EventSink sink) throws Exception {
        String normalized = baseUrl.replaceAll("/+$", "");
        Exception lastError = null;
        Object jsonRpcUrl = this.discoverA2aUrl(normalized, token);
        if (jsonRpcUrl == null || ((String)jsonRpcUrl).isBlank()) {
            jsonRpcUrl = normalized + "/a2a";
        }
        log.info("A2A JSON-RPC endpoint: {}", jsonRpcUrl);
        try {
            this.ensureNotCancelled(taskKey);
            this.streamA2a(this.a2aJsonRpcRequest((String)jsonRpcUrl, token, "message/stream", instruction).timeout(Duration.ofMinutes(10L)).build(), sink, taskKey);
            return;
        }
        catch (Exception error) {
            lastError = error;
            if (this.abortFallback(taskKey, error)) {
                throw error;
            }
            log.debug("A2A message/stream failed, fallback: {}", (Object)error.getMessage());
            try {
                this.ensureNotCancelled(taskKey);
                this.sendA2a(this.a2aJsonRpcRequest((String)jsonRpcUrl, token, "message/send", instruction).timeout(Duration.ofMinutes(10L)).build(), sink);
                return;
            }
            catch (Exception error2) {
                lastError = error2;
                if (this.abortFallback(taskKey, error2)) {
                    throw error2;
                }
                log.debug("A2A message/send failed, fallback: {}", (Object)error2.getMessage());
                try {
                    this.ensureNotCancelled(taskKey);
                    this.streamA2a(this.a2aMessageRequest(normalized + "/message/stream", token, instruction).timeout(Duration.ofMinutes(10L)).build(), sink, taskKey);
                    return;
                }
                catch (Exception error3) {
                    lastError = error3;
                    if (this.abortFallback(taskKey, error3)) {
                        throw error3;
                    }
                    log.debug("A2A legacy /message/stream failed, fallback: {}", (Object)error3.getMessage());
                    try {
                        this.ensureNotCancelled(taskKey);
                        this.sendA2a(this.a2aMessageRequest(normalized + "/message/send", token, instruction).timeout(Duration.ofMinutes(10L)).build(), sink);
                        return;
                    }
                    catch (Exception error4) {
                        lastError = error4;
                        throw lastError == null ? new IllegalStateException("A2A Agent \u8c03\u7528\u5931\u8d25") : lastError;
                    }
                }
            }
        }
    }

    private boolean abortFallback(String taskKey, Exception error) {
        if (this.isCancelled(taskKey)) {
            return true;
        }
        if (error instanceof HttpSurfaceException) {
            HttpSurfaceException surface = (HttpSurfaceException)error;
            return surface.status != 404 && surface.status != 405 && surface.status != 501;
        }
        return false;
    }

    private void sendA2a(HttpRequest request, EventSink sink) throws Exception {
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new HttpSurfaceException(response.statusCode(), "A2A Agent \u8fd4\u56de HTTP " + response.statusCode() + "\uff1a" + this.abbreviate(response.body()));
        }
        JsonNode payload = this.objectMapper.readTree(response.body());
        String text = this.extractA2aText(payload);
        if (!text.isBlank()) {
            sink.onChunk(text);
        }
    }

    private HttpRequest.Builder a2aMessageRequest(String url, String token, String instruction) throws Exception {
        Map<String, Object> body = Map.of("message", Map.of("role", "user", "parts", List.of(Map.of("kind", "text", "text", instruction))));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(body)));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private HttpRequest.Builder a2aJsonRpcRequest(String url, String token, String method, String instruction) throws Exception {
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", "a2a-" + System.currentTimeMillis(), "method", method, "params", Map.of("message", Map.of("role", "user", "parts", List.of(Map.of("kind", "text", "text", instruction)), "messageId", "msg-" + System.currentTimeMillis(), "kind", "message")));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(body)));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private void streamA2a(HttpRequest request, EventSink sink) throws Exception {
        this.streamA2a(request, sink, null);
    }

    private void streamA2a(HttpRequest request, EventSink sink, String taskKey) throws Exception {
        HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String errorBody;
            try (InputStream stream = response.body();){
                errorBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new HttpSurfaceException(response.statusCode(), "A2A Agent \u8fd4\u56de HTTP " + response.statusCode() + "\uff1a" + this.abbreviate(errorBody));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));){
            String line;
            StringBuilder dataLines = new StringBuilder();
            long lastEventAt = System.currentTimeMillis();
            while ((line = this.readLineWithIdleClose(reader, taskKey, lastEventAt)) != null) {
                this.ensureNotCancelled(taskKey);
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    if (value.startsWith(" ")) {
                        value = value.substring(1);
                    }
                    if (dataLines.length() > 0) {
                        dataLines.append("\n");
                    }
                    dataLines.append(value);
                    continue;
                }
                if (!line.isBlank() || dataLines.length() <= 0) continue;
                boolean terminal = this.dispatchA2a(dataLines.toString(), sink);
                dataLines.setLength(0);
                lastEventAt = System.currentTimeMillis();
                if (!terminal) continue;
                return;
            }
            if (dataLines.length() > 0) {
                this.dispatchA2a(dataLines.toString(), sink);
            }
        }
    }

    private boolean dispatchA2a(String raw, EventSink sink) {
        block7: {
            try {
                JsonNode payload = this.objectMapper.readTree(raw);
                JsonNode error = payload.path("error");
                if (!error.isMissingNode() && !error.isNull() && error.isObject()) {
                    throw new IllegalStateException("A2A Agent \u9519\u8bef " + error.path("code").asText("") + "\uff1a" + error.path("message").asText(""));
                }
                JsonNode result = payload.path("result");
                if (result.isObject() && "status-update".equals(result.path("kind").asText())) {
                    String state = result.path("status").path("state").asText("");
                    if ("failed".equals(state) || "rejected".equals(state) || "canceled".equals(state)) {
                        String detail = result.path("status").path("message").path("parts").path(0).path("text").asText("");
                        throw new IllegalStateException("A2A \u4efb\u52a1" + ("canceled".equals(state) ? "\u5df2\u53d6\u6d88" : "\u5931\u8d25") + "\uff1a" + detail);
                    }
                    return "completed".equals(state);
                }
                String text = this.extractA2aText(payload);
                if (!text.isBlank()) {
                    sink.onChunk(text);
                }
            }
            catch (IllegalStateException e) {
                throw e;
            }
            catch (Exception e) {
                if (raw.isBlank()) break block7;
                sink.onChunk(raw);
            }
        }
        return false;
    }

    private String discoverA2aUrl(String normalized, String token) {
        for (String path : new String[]{"/.well-known/agent-card.json", "/.well-known/agent.json"}) {
            try {
                JsonNode url;
                HttpResponse<String> response;
                HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(normalized + path)).timeout(Duration.ofSeconds(8L)).GET();
                if (token != null && !token.isBlank()) {
                    builder.header("Authorization", "Bearer " + token.trim());
                }
                if ((response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())).statusCode() != 200 || !(url = this.objectMapper.readTree(response.body()).path("url")).isTextual() || url.asText().isBlank()) continue;
                String endpoint = url.asText().replaceAll("/+$", "");
                if (endpoint.startsWith("/")) {
                    endpoint = normalized + endpoint;
                }
                return endpoint;
            }
            catch (Exception e) {
                log.debug("A2A card discover failed via {}: {}", (Object)path, (Object)e.getMessage());
            }
        }
        return null;
    }

    public void runLocal(String instruction, String channelCode, String approvalMode, EventSink sink) throws Exception {
        this.runLocal(instruction, channelCode, approvalMode, sink, null, null);
    }

    public void runLocal(String instruction, String channelCode, String approvalMode, EventSink sink, String cwd, List<String> sandboxRoots) throws Exception {
        this.runLocal(null, instruction, channelCode, approvalMode, sink, cwd, sandboxRoots);
    }

    public void runLocal(String taskKey, String instruction, String channelCode, String approvalMode, EventSink sink, String cwd, List<String> sandboxRoots) throws Exception {
        String port = System.getProperty("server.port", "8080");
        String url = "http://127.0.0.1:" + port + "/api/agent/stream";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(10L)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(this.buildBody(instruction, channelCode, approvalMode, cwd, sandboxRoots))).build();
        this.stream(request, sink, taskKey);
    }

    public void cancel(String taskKey) {
        if (taskKey != null) {
            this.cancelled.add(taskKey);
        }
    }

    public void clearCancelled(String taskKey) {
        if (taskKey != null) {
            this.cancelled.remove(taskKey);
        }
    }

    private boolean isCancelled(String taskKey) {
        return taskKey != null && this.cancelled.contains(taskKey);
    }

    private void ensureNotCancelled(String taskKey) {
        if (this.isCancelled(taskKey)) {
            throw new CancellationException("\u4efb\u52a1\u5df2\u88ab\u53d6\u6d88");
        }
    }

    private String buildBody(String instruction, String channelCode, String approvalMode, String cwd, List<String> sandboxRoots) throws Exception {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("agentId", "collab-" + System.currentTimeMillis());
        body.put("message", instruction);
        if (cwd != null && !cwd.isBlank()) {
            body.put("cwd", cwd);
        }
        if (sandboxRoots != null && !sandboxRoots.isEmpty()) {
            body.put("sandboxRoots", sandboxRoots);
        }
        if (channelCode != null && !channelCode.isBlank()) {
            body.put("channelCode", channelCode);
        }
        body.put("approvalMode", approvalMode == null || approvalMode.isBlank() ? "AUTO_APPROVE" : approvalMode);
        return this.objectMapper.writeValueAsString(body);
    }

    private void stream(HttpRequest request, EventSink sink) throws Exception {
        this.stream(request, sink, null);
    }

    private void stream(HttpRequest request, EventSink sink, String taskKey) throws Exception {
        HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String errorBody;
            try (InputStream stream = response.body();){
                errorBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new HttpSurfaceException(response.statusCode(), "Agent \u670d\u52a1\u8fd4\u56de HTTP " + response.statusCode() + "\uff1a" + this.abbreviate(errorBody));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));){
            String line;
            String eventName = "";
            StringBuilder dataLines = new StringBuilder();
            long lastEventAt = System.currentTimeMillis();
            while ((line = this.readLineWithIdleClose(reader, taskKey, lastEventAt, sink)) != null) {
                this.ensureNotCancelled(taskKey);
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    if (value.startsWith(" ")) {
                        value = value.substring(1);
                    }
                    if (dataLines.length() > 0) {
                        dataLines.append("\n");
                    }
                    dataLines.append(value);
                    continue;
                }
                if (!line.isBlank()) continue;
                if (dataLines.length() > 0) {
                    boolean terminal = this.dispatch(eventName, dataLines.toString(), sink);
                    dataLines.setLength(0);
                    eventName = "";
                    lastEventAt = System.currentTimeMillis();
                    if (!terminal) continue;
                    return;
                }
                if (!this.isTerminalEvent(eventName)) continue;
                return;
            }
            if (dataLines.length() > 0) {
                this.dispatch(eventName, dataLines.toString(), sink);
            }
        }
    }

    private String readLineWithIdleClose(BufferedReader reader, String taskKey, long lastEventAt) throws Exception {
        return this.readLineWithIdleClose(reader, taskKey, lastEventAt, null);
    }

    private String readLineWithIdleClose(BufferedReader reader, String taskKey, long lastEventAt, EventSink sink) throws Exception {
        while (!reader.ready()) {
            long idleLimit;
            this.ensureNotCancelled(taskKey);
            boolean approvalAwait = sink != null && sink.approvalAwaiting();
            long l = idleLimit = approvalAwait ? 700000L : 300000L;
            if (System.currentTimeMillis() - lastEventAt >= idleLimit) {
                throw new IllegalStateException("Agent \u6d41\u7a7a\u95f2\u8d85\u8fc7 " + idleLimit / 1000L + "s \u65e0\u4e8b\u4ef6\uff0c\u7f51\u5173\u4e3b\u52a8\u65ad\u5f00\uff08taskKey=" + taskKey + (approvalAwait ? "\uff0c\u5ba1\u6279\u7b49\u5f85\u4e2d" : "") + "\uff09");
            }
            Thread.sleep(200L);
        }
        return reader.readLine();
    }

    private boolean dispatch(String eventName, String raw, EventSink sink) {
        String normalizedEventName;
        String string = normalizedEventName = eventName == null || eventName.isBlank() ? "chunk" : eventName;
        if (this.isTerminalEvent(normalizedEventName)) {
            return true;
        }
        try {
            JsonNode payload = this.objectMapper.readTree(raw);
            switch (normalizedEventName) {
                case "chunk": 
                case "message.delta": {
                    sink.onChunk(this.extractText(payload));
                    break;
                }
                case "reasoning": 
                case "message.reasoning": {
                    sink.onReasoning(this.extractText(payload));
                    break;
                }
                case "tool_result": 
                case "tool.result": {
                    String toolName = payload.path("toolName").asText(payload.path("name").asText("tool"));
                    String callId = payload.path("callId").asText("");
                    JsonNode resultNode = payload.path("result");
                    String rawResult = resultNode.isMissingNode() ? "" : this.rawJson(resultNode);
                    String summary = this.abbreviate(resultNode.isMissingNode() ? this.extractText(payload) : this.extractText(resultNode));
                    String status = payload.path("status").asText("");
                    if ("running".equals(status) || "call".equals(status)) {
                        sink.onToolCall(toolName, summary, callId);
                        break;
                    }
                    sink.onToolResult(toolName, summary, rawResult, callId);
                    break;
                }
                case "step_break": 
                case "tool.call": {
                    String toolName = payload.path("toolName").asText("tool");
                    String callId = payload.path("callId").asText("");
                    String status = payload.path("status").asText("");
                    String summary = this.abbreviate(payload.toString());
                    if (status.contains("approval") || status.contains("pending") || "waiting".equals(status) || "requires_approval".equals(status)) {
                        sink.onApprovalRequired(toolName, summary);
                        break;
                    }
                    sink.onToolCall(toolName, summary, callId);
                    break;
                }
                case "error": {
                    throw new IllegalStateException(payload.path("message").asText(this.extractText(payload)));
                }
            }
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            if ("chunk".equals(eventName) || eventName == null || eventName.isBlank()) {
                sink.onChunk(raw);
            }
            log.debug("Skip unparsable SSE event {}: {}", (Object)eventName, (Object)e.getMessage());
        }
        return false;
    }

    private boolean isTerminalEvent(String eventName) {
        return "done".equals(eventName);
    }

    private String extractText(JsonNode payload) {
        String nestedText;
        if (payload.isTextual()) {
            return payload.asText();
        }
        JsonNode nestedPayload = payload.path("payload");
        if (!(nestedPayload.isMissingNode() || nestedPayload.isNull() || (nestedText = this.extractText(nestedPayload)).isBlank())) {
            return nestedText;
        }
        for (String field : new String[]{"text", "content", "delta", "message", "result"}) {
            JsonNode node = payload.path(field);
            if (!node.isTextual()) continue;
            return node.asText();
        }
        return payload.toString();
    }

    private String extractA2aText(JsonNode payload) {
        String text;
        String text2;
        String text3;
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "";
        }
        if (payload.isTextual()) {
            return payload.asText();
        }
        JsonNode result = payload.path("result");
        if (!result.isMissingNode() && !(text3 = this.extractA2aText(result)).isBlank()) {
            return text3;
        }
        JsonNode message = payload.path("message");
        if (!message.isMissingNode() && !(text2 = this.extractA2aText(message)).isBlank()) {
            return text2;
        }
        JsonNode artifact = payload.path("artifact");
        if (!artifact.isMissingNode() && !(text = this.extractA2aText(artifact)).isBlank()) {
            return text;
        }
        JsonNode parts = payload.path("parts");
        if (parts.isArray()) {
            StringBuilder builder = new StringBuilder();
            parts.forEach(part -> {
                String partText = part.path("text").asText("");
                if (!partText.isBlank()) {
                    builder.append(partText);
                }
            });
            if (builder.length() > 0) {
                return builder.toString();
            }
        }
        for (String field : new String[]{"text", "content", "delta", "message", "result"}) {
            JsonNode node = payload.path(field);
            if (!node.isTextual()) continue;
            return node.asText();
        }
        return "";
    }

    private String rawJson(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "";
        }
        return payload.isTextual() ? payload.asText() : payload.toString();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) + "\u2026" : singleLine;
    }

    public static interface EventSink {
        public void onChunk(String var1);

        public void onReasoning(String var1);

        public void onToolCall(String var1, String var2);

        default public void onToolCall(String toolName, String summary, String callId) {
            this.onToolCall(toolName, summary);
        }

        public void onToolResult(String var1, String var2);

        default public void onToolResult(String toolName, String summary, String rawResult) {
            this.onToolResult(toolName, summary);
        }

        default public void onToolResult(String toolName, String summary, String rawResult, String callId) {
            this.onToolResult(toolName, summary, rawResult);
        }

        default public void onApprovalRequired(String toolName, String summary) {
        }

        default public boolean approvalAwaiting() {
            return false;
        }
    }

    static class HttpSurfaceException
    extends IllegalStateException {
        final int status;

        HttpSurfaceException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public static class ApprovalInterruptException
    extends Exception {
        public ApprovalInterruptException(String message) {
            super(message);
        }
    }
}

