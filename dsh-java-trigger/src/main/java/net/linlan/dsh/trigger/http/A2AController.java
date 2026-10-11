package net.linlan.dsh.trigger.http;

import net.linlan.dsh.api.IAgentApi;
import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.api.response.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(value={"/a2a"})
public class A2AController {
    private static final Logger log = LoggerFactory.getLogger(A2AController.class);
    private static final long SSE_TIMEOUT_MS = 600000L;
    private static final long SSE_HEARTBEAT_INTERVAL_MS = 15000L;
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int TASK_NOT_FOUND = -32001;
    private static final int TASK_NOT_CANCELABLE = -32002;
    private final IAgentApi agentApi;
    private final ObjectMapper objectMapper;
    private final ExecutorService a2aExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final Map<String, A2aTask> tasks = new ConcurrentHashMap<String, A2aTask>();

    public A2AController(IAgentApi agentApi, ObjectMapper objectMapper) {
        this.agentApi = agentApi;
        this.objectMapper = objectMapper;
        this.a2aExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), runnable -> {
            Thread thread = new Thread(runnable, "a2a-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "a2a-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostMapping
    public Object handle(@RequestBody(required=false) JsonNode request) {
        if (request == null || request.isMissingNode() || !"2.0".equals(request.path("jsonrpc").asText(null)) || request.path("method").asText(null) == null) {
            return this.errorResponse(this.idOf(request), -32600, "Invalid Request");
        }
        JsonNode id = this.idOf(request);
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        log.info("[A2A] \u6536\u5230\u8bf7\u6c42 method={} id={}", (Object)method, (Object)id);
        return switch (method) {
            case "message/send" -> this.messageSend(id, params);
            case "message/stream" -> this.messageStream(id, params);
            case "tasks/get" -> this.tasksGet(id, params);
            case "tasks/cancel" -> this.tasksCancel(id, params);
            default -> this.errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    @ExceptionHandler(value={HttpMessageNotReadableException.class})
    public Map<String, Object> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("[A2A] \u8bf7\u6c42\u4f53\u89e3\u6790\u5931\u8d25\uff1a{}", (Object)exception.getMessage());
        return this.errorResponse(null, -32700, "Parse error");
    }

    private Object messageSend(JsonNode id, JsonNode params) {
        JsonNode messageNode = params.path("message");
        String text = this.extractText(messageNode);
        if (messageNode.isMissingNode() || text.isBlank()) {
            return this.errorResponse(id, -32602, "Invalid params: message with text parts is required");
        }
        A2aTask task = this.createTask(messageNode);
        try {
            this.execute(task, text, null, null, null);
            task.complete();
        }
        catch (Exception exception) {
            log.warn("[A2A] message/send \u4efb\u52a1\u6267\u884c\u5931\u8d25\uff1a{}", (Object)exception.getMessage());
            task.fail(exception.getMessage() == null ? "\u4efb\u52a1\u6267\u884c\u5931\u8d25" : exception.getMessage());
        }
        return this.resultResponse(id, task.snapshot());
    }

    private Object messageStream(JsonNode id, JsonNode params) {
        JsonNode messageNode = params.path("message");
        String text = this.extractText(messageNode);
        if (messageNode.isMissingNode() || text.isBlank()) {
            return this.errorResponse(id, -32602, "Invalid params: message with text parts is required");
        }
        SseEmitter emitter = new SseEmitter(Long.valueOf(600000L));
        AtomicBoolean heartbeatStopped = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = this.startHeartbeat(emitter, heartbeatStopped);
        Runnable stopHeartbeat = () -> {
            heartbeatStopped.set(true);
            heartbeat.cancel(false);
        };
        emitter.onCompletion(stopHeartbeat);
        emitter.onTimeout(stopHeartbeat);
        emitter.onError(error -> stopHeartbeat.run());
        this.a2aExecutor.execute(() -> {
            try {
                this.streamTask(id, messageNode, text, emitter);
            }
            finally {
                stopHeartbeat.run();
            }
        });
        return emitter;
    }

    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, AtomicBoolean stopped) {
        return this.heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (stopped.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            }
            catch (Exception exception) {
                stopped.set(true);
            }
        }, 15000L, 15000L, TimeUnit.MILLISECONDS);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void streamTask(JsonNode id, JsonNode messageNode, String text, SseEmitter emitter) {
        A2aTask task;
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> ((SseEmitter)emitter).complete());
        emitter.onError(error -> closed.set(true));
        try {
            task = this.createTask(messageNode);
        }
        catch (Exception exception) {
            this.sendResult(emitter, closed, id, this.failureStatusEvent("unknown", "unknown", exception));
            emitter.complete();
            return;
        }
        task.appendText("");
        try {
            this.sendResult(emitter, closed, id, task.snapshot());
            StringBuilder emitted = new StringBuilder();
            this.execute(task, text, delta -> {
                if (delta == null || delta.isEmpty()) {
                    return;
                }
                boolean first = emitted.length() == 0;
                emitted.append((String)delta);
                this.sendResult(emitter, closed, id, this.artifactUpdateEvent(task, (String)delta, !first, false));
            }, toolEvent -> this.sendResult(emitter, closed, id, this.toolStatusEvent(task, (AgentToolCallEventResponseDTO)toolEvent)), () -> {});
            task.complete();
            this.sendResult(emitter, closed, id, this.statusUpdateEvent(task, true, null));
        }
        catch (Exception exception) {
            log.warn("[A2A] \u6d41\u5f0f\u4efb\u52a1\u6267\u884c\u5931\u8d25\uff1a{}", (Object)exception.getMessage());
            task.fail(exception.getMessage() == null ? "\u4efb\u52a1\u6267\u884c\u5931\u8d25" : exception.getMessage());
            this.sendResult(emitter, closed, id, this.statusUpdateEvent(task, true, null));
        }
        finally {
            emitter.complete();
        }
    }

    private void execute(A2aTask task, String text, Consumer<String> deltaSink, Consumer<AgentToolCallEventResponseDTO> toolSink, Runnable finishSink) {
        Runnable effectiveFinishSink;
        Consumer<AgentToolCallEventResponseDTO> effectiveToolSink;
        Consumer<String> effectiveDeltaSink;
        AgentMessageRequestDTO request = new AgentMessageRequestDTO(task.agentId(), null, null, null, text, null, "AUTO_APPROVE", null, null);
        Response response = this.agentApi.sendMessageStreaming(request, effectiveDeltaSink = deltaSink == null ? chunk -> task.appendText((String)chunk) : chunk -> {
            task.appendText((String)chunk);
            deltaSink.accept((String)chunk);
        }, reasoning -> {}, effectiveToolSink = toolSink == null ? toolEvent -> {} : toolSink, effectiveFinishSink = finishSink == null ? () -> {} : finishSink);
        if (response == null || response.data() == null) {
            throw new IllegalStateException(response == null ? "Agent \u65e0\u54cd\u5e94" : response.info());
        }
    }

    private Object tasksGet(JsonNode id, JsonNode params) {
        A2aTask task;
        String taskId = params.path("id").asText(null);
        A2aTask a2aTask = task = taskId == null ? null : this.tasks.get(taskId);
        if (task == null) {
            return this.errorResponse(id, -32001, "Task not found: " + taskId);
        }
        return this.resultResponse(id, task.snapshot());
    }

    private Object tasksCancel(JsonNode id, JsonNode params) {
        A2aTask task;
        String taskId = params.path("id").asText(null);
        A2aTask a2aTask = task = taskId == null ? null : this.tasks.get(taskId);
        if (task == null) {
            return this.errorResponse(id, -32001, "Task not found: " + taskId);
        }
        if (task.isTerminal()) {
            return this.errorResponse(id, -32002, "Task cannot be canceled: already in terminal state " + task.state());
        }
        try {
            this.agentApi.cancelAgent(task.agentId());
        }
        catch (Exception exception) {
            log.warn("[A2A] \u53d6\u6d88 Agent {} \u5931\u8d25\uff1a{}", (Object)task.agentId(), (Object)exception.getMessage());
        }
        task.cancel();
        return this.resultResponse(id, task.snapshot());
    }

    private A2aTask createTask(JsonNode messageNode) {
        Object contextId = messageNode.path("contextId").asText("");
        if (((String)contextId).isBlank()) {
            contextId = "ctx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        A2aTask task = new A2aTask("a2a-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), (String)contextId, "a2a-" + (String)contextId, this.normalizeMessage(messageNode, "user"));
        this.tasks.put(task.id(), task);
        return task;
    }

    private Map<String, Object> normalizeMessage(JsonNode messageNode, String fallbackRole) {
        String contextId;
        LinkedHashMap<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", messageNode.path("role").asText(fallbackRole));
        message.put("parts", this.extractParts(messageNode));
        String messageId = messageNode.path("messageId").asText("");
        message.put("messageId", messageId.isBlank() ? "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) : messageId);
        String taskId = messageNode.path("taskId").asText("");
        if (!taskId.isBlank()) {
            message.put("taskId", taskId);
        }
        if (!(contextId = messageNode.path("contextId").asText("")).isBlank()) {
            message.put("contextId", contextId);
        }
        message.put("kind", "message");
        return message;
    }

    private List<Map<String, Object>> extractParts(JsonNode messageNode) {
        ArrayList<Map<String, Object>> parts = new ArrayList<Map<String, Object>>();
        for (JsonNode part : messageNode.path("parts")) {
            String kind = part.path("kind").asText("text");
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<String, Object>();
            switch (kind) {
                case "text": {
                    normalized.put("kind", "text");
                    normalized.put("text", part.path("text").asText(""));
                    break;
                }
                case "data": {
                    normalized.put("kind", "text");
                    normalized.put("text", part.path("data").toString());
                    break;
                }
                case "file": {
                    normalized.put("kind", "text");
                    JsonNode file = part.path("file");
                    normalized.put("text", "[file] " + file.path("name").asText(file.path("uri").asText("attachment")));
                    break;
                }
                default: {
                    normalized.put("kind", "text");
                    normalized.put("text", part.toString());
                }
            }
            parts.add(normalized);
        }
        if (parts.isEmpty()) {
            parts.add(Map.of("kind", "text", "text", ""));
        }
        return parts;
    }

    private String extractText(JsonNode messageNode) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : messageNode.path("parts")) {
            if (part.has("text")) {
                builder.append(part.path("text").asText());
                continue;
            }
            if (!part.has("data")) continue;
            builder.append(part.path("data").toString());
        }
        return builder.toString();
    }

    private Map<String, Object> artifactUpdateEvent(A2aTask task, String delta, boolean append, boolean lastChunk) {
        LinkedHashMap<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("artifactId", task.artifactId());
        artifact.put("name", "response");
        artifact.put("parts", List.of(Map.of("kind", "text", "text", delta)));
        LinkedHashMap<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("taskId", task.id());
        event.put("contextId", task.contextId());
        event.put("artifact", artifact);
        event.put("append", append);
        event.put("lastChunk", lastChunk);
        event.put("kind", "artifact-update");
        return event;
    }

    private Map<String, Object> statusUpdateEvent(A2aTask task, boolean finalFlag, String failureMessage) {
        LinkedHashMap<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", task.state());
        status.put("timestamp", task.updatedAt());
        if (failureMessage != null && !failureMessage.isBlank()) {
            status.put("message", this.agentMessage(failureMessage));
        }
        LinkedHashMap<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("taskId", task.id());
        event.put("contextId", task.contextId());
        event.put("status", status);
        event.put("final", finalFlag);
        event.put("kind", "status-update");
        return event;
    }

    private Map<String, Object> toolStatusEvent(A2aTask task, AgentToolCallEventResponseDTO toolEvent) {
        boolean isResult = "result".equals(toolEvent.phase());
        String summary = (isResult ? "\u5de5\u5177\u8fd4\u56de " : "\u8c03\u7528\u5de5\u5177 ") + toolEvent.toolName();
        LinkedHashMap<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", "working");
        status.put("timestamp", Instant.now().toString());
        status.put("message", this.agentMessage(summary));
        LinkedHashMap<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("taskId", task.id());
        event.put("contextId", task.contextId());
        event.put("status", status);
        event.put("final", false);
        event.put("kind", "status-update");
        return event;
    }

    private Map<String, Object> failureStatusEvent(String taskId, String contextId, Exception exception) {
        LinkedHashMap<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", "failed");
        status.put("timestamp", Instant.now().toString());
        status.put("message", this.agentMessage(exception.getMessage() == null ? "\u4efb\u52a1\u6267\u884c\u5931\u8d25" : exception.getMessage()));
        LinkedHashMap<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("taskId", taskId);
        event.put("contextId", contextId);
        event.put("status", status);
        event.put("final", true);
        event.put("kind", "status-update");
        return event;
    }

    private Map<String, Object> agentMessage(String text) {
        LinkedHashMap<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "agent");
        message.put("parts", List.of(Map.of("kind", "text", "text", text)));
        message.put("messageId", "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        message.put("kind", "message");
        return message;
    }

    private JsonNode idOf(JsonNode request) {
        if (request == null || request.isMissingNode() || !request.hasNonNull("id")) {
            return null;
        }
        return request.path("id");
    }

    private Map<String, Object> resultResponse(JsonNode id, Object result) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> errorResponse(JsonNode id, int code, String message) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private void sendResult(SseEmitter emitter, AtomicBoolean closed, JsonNode id, Object result) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data((Object)this.objectMapper.writeValueAsString(this.resultResponse(id, result))));
        }
        catch (IOException exception) {
            closed.set(true);
        }
    }

    private final class A2aTask {
        private final String id;
        private final String contextId;
        private final String agentId;
        private final String artifactId;
        private final Map<String, Object> userMessage;
        private final StringBuilder text = new StringBuilder();
        private volatile String state = "working";
        private volatile String failureMessage;
        private volatile Instant updatedAt = Instant.now();

        private A2aTask(String id, String contextId, String agentId, Map<String, Object> userMessage) {
            this.id = id;
            this.contextId = contextId;
            this.agentId = agentId;
            this.artifactId = "art-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            this.userMessage = userMessage;
        }

        String id() {
            return this.id;
        }

        String contextId() {
            return this.contextId;
        }

        String agentId() {
            return this.agentId;
        }

        String artifactId() {
            return this.artifactId;
        }

        synchronized void appendText(String delta) {
            this.text.append(delta == null ? "" : delta);
            this.updatedAt = Instant.now();
        }

        synchronized void complete() {
            this.state = "completed";
            this.updatedAt = Instant.now();
        }

        synchronized void fail(String message) {
            this.state = "failed";
            this.failureMessage = message;
            this.updatedAt = Instant.now();
        }

        synchronized void cancel() {
            this.state = "canceled";
            this.updatedAt = Instant.now();
        }

        String state() {
            return this.state;
        }

        String updatedAt() {
            return this.updatedAt.toString();
        }

        boolean isTerminal() {
            return "completed".equals(this.state) || "failed".equals(this.state) || "canceled".equals(this.state) || "rejected".equals(this.state);
        }

        synchronized Map<String, Object> snapshot() {
            LinkedHashMap<String, Object> snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("kind", "task");
            snapshot.put("id", this.id);
            snapshot.put("contextId", this.contextId);
            LinkedHashMap<String, Object> status = new LinkedHashMap<String, Object>();
            status.put("state", this.state);
            status.put("timestamp", this.updatedAt.toString());
            if (this.failureMessage != null && !this.failureMessage.isBlank()) {
                status.put("message", A2AController.this.agentMessage(this.failureMessage));
            }
            snapshot.put("status", status);
            String artifactText = this.text.toString();
            if (!artifactText.isBlank()) {
                snapshot.put("artifacts", List.of(Map.of("artifactId", this.artifactId, "name", "response", "parts", List.of(Map.of("kind", "text", "text", artifactText)))));
            } else {
                snapshot.put("artifacts", List.of());
            }
            ArrayList<Map<String, Object>> history = new ArrayList<Map<String, Object>>();
            history.add(this.userMessage);
            if (!artifactText.isBlank()) {
                history.add(A2AController.this.agentMessage(artifactText));
            }
            snapshot.put("history", history);
            return snapshot;
        }
    }
}

