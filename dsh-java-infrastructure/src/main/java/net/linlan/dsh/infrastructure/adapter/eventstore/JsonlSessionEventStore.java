package net.linlan.dsh.infrastructure.adapter.eventstore;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ReasoningBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.model.valobj.ToolResultBlock;
import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.valobj.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * JSONL file-based implementation of {@link SessionPersistenceSeam}.
 * <p>
 * Each session is persisted as two files under the base directory:
 * <ul>
 *   <li>{@code <sessionId>/header.json} — session header</li>
 *   <li>{@code <sessionId>/events.jsonl} — append-only event log (one JSON per line)</li>
 * </ul>
 * Mirrors TS {@code session-persistence-jsonl}.
 *
 *
 */
public class JsonlSessionEventStore implements SessionPersistenceSeam {

    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Object> fileLocks = new ConcurrentHashMap<>();

    /**
     * 处理Jsonl 会话 事件 Store。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param baseDir base Dir参数。
     * @return 方法执行结果。
     */
    public JsonlSessionEventStore(String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void saveHeader(SessionHeader header) {
        try {
            Path dir = sessionDir(header.sessionId());
            Files.createDirectories(dir);
            Path headerFile = dir.resolve("header.json");
            ObjectNode node = headerJson(header);
            Files.writeString(headerFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save header for session " + header.sessionId(), e);
        }
    }
    /**
     * 获取Header。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 获取到的Header。
     */


    @Override
    public Optional<SessionHeader> getHeader(String sessionId) {
        Path headerFile = sessionDir(sessionId).resolve("header.json");
        if (!Files.exists(headerFile)) {
            return Optional.empty();
        }
        try {
            var node = objectMapper.readTree(Files.readString(headerFile, StandardCharsets.UTF_8));
            int version = node.has("version") ? node.get("version").asInt() : 1;
            if (version > SessionHeader.SESSION_FORMAT_VERSION) {
                throw new IllegalStateException("session uses unsupported format v" + version);
            }
            SessionHeader header = new SessionHeader(
                    version,
                    node.get("sessionId").asText(),
                    Instant.parse(node.get("createdAt").asText()),
                    node.has("cwd") ? node.get("cwd").asText() : null,
                    node.has("parentSession") ? node.get("parentSession").asText() : null,
                    node.has("seedLength") ? Math.max(0, node.get("seedLength").asInt()) : 0,
                    node.has("origin") ? node.get("origin").asText() : null,
                    node.has("delegationDepth") ? node.get("delegationDepth").asInt() : 0,
                    node.has("agentPreset") ? node.get("agentPreset").asText() : null,
                    node.has("formatVersion") ? node.get("formatVersion").asInt() : 0
            );
            if (version < SessionHeader.SESSION_FORMAT_VERSION) {
                header = migrate(header);
                saveHeader(header);
            }
            return Optional.of(header);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read header for session " + sessionId, e);
        }
    }

    private SessionHeader migrate(SessionHeader legacy) {
        return new SessionHeader(
                SessionHeader.SESSION_FORMAT_VERSION,
                legacy.sessionId(),
                legacy.createdAt(),
                legacy.cwd(),
                legacy.parentSession(),
                legacy.seedLength(),
                legacy.origin(),
                legacy.delegationDepth(),
                legacy.agentPreset(),
                legacy.formatVersion()
        );
    }

    private ObjectNode headerJson(SessionHeader header) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("version", SessionHeader.SESSION_FORMAT_VERSION);
        node.put("sessionId", header.sessionId());
        node.put("createdAt", header.createdAt().toString());
        node.put("cwd", header.cwd());
        if (header.parentSession() != null) node.put("parentSession", header.parentSession());
        if (header.seedLength() >= 0) node.put("seedLength", header.seedLength());
        if (header.origin() != null) node.put("origin", header.origin());
        node.put("delegationDepth", header.delegationDepth());
        if (header.agentPreset() != null) node.put("agentPreset", header.agentPreset());
        node.put("formatVersion", header.formatVersion());
        return node;
    }
    /**
     * 追加目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param event 领域事件。
     */


    @Override
    public void append(String sessionId, SessionEvent event) {
        Object lock = fileLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            try {
                Path eventsFile = sessionDir(sessionId).resolve("events.jsonl");
                Files.createDirectories(eventsFile.getParent());
                String json = serializeEvent(sessionId, event);
                Files.writeString(eventsFile, json + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new RuntimeException("Failed to append event to session " + sessionId, e);
            }
        }
    }
    /**
     * 追加Batch。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param events events参数。
     */


    @Override
    public void appendBatch(String sessionId, List<SessionEvent> events) {
        Object lock = fileLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            try {
                Path eventsFile = sessionDir(sessionId).resolve("events.jsonl");
                Files.createDirectories(eventsFile.getParent());
                StringBuilder sb = new StringBuilder();
                for (SessionEvent event : events) {
                    sb.append(serializeEvent(sessionId, event)).append("\n");
                }
                Files.writeString(eventsFile, sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new RuntimeException("Failed to append batch to session " + sessionId, e);
            }
        }
    }
    /**
     * 读取All。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> readAll(String sessionId) {
        return readEvents(sessionId, 0, Long.MAX_VALUE);
    }
    /**
     * 读取After。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param afterSeq after Seq参数。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> readAfter(String sessionId, long afterSeq) {
        return readEvents(sessionId, afterSeq + 1, Long.MAX_VALUE);
    }
    /**
     * 读取Range。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param fromSeq from Seq参数。
     * @param toSeq to Seq参数。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> readRange(String sessionId, long fromSeq, long toSeq) {
        return readEvents(sessionId, fromSeq, toSeq);
    }
    /**
     * 获取Last Seq。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 获取到的Last Seq。
     */


    @Override
    public long getLastSeq(String sessionId) {
        Path eventsFile = sessionDir(sessionId).resolve("events.jsonl");
        if (!Files.exists(eventsFile)) {
            return -1;
        }
        long lastSeq = -1;
        try {
            for (String line : Files.readAllLines(eventsFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                var node = objectMapper.readTree(line);
                long seq = node.get("seq").asLong();
                if (seq > lastSeq) lastSeq = seq;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read last seq for session " + sessionId, e);
        }
        return lastSeq;
    }
    /**
     * count。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public long count(String sessionId) {
        Path eventsFile = sessionDir(sessionId).resolve("events.jsonl");
        if (!Files.exists(eventsFile)) {
            return 0;
        }
        try {
            return Files.lines(eventsFile, StandardCharsets.UTF_8)
                    .filter(line -> !line.isBlank())
                    .count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count events for session " + sessionId, e);
        }
    }
    /**
     * 列出Headers。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param limit limit参数。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionHeader> listHeaders(int limit) {
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }
        try (var stream = Files.list(baseDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(dir -> {
                        try {
                            return getHeader(dir.getFileName().toString());
                        } catch (Exception e) {
                            return java.util.Optional.<SessionHeader>empty();
                        }
                    })
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .limit(limit)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list session headers", e);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────

    /**
     * 读取 Events。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param fromSeq from Seq参数。
     * @param toSeq to Seq参数。
     * @return 方法执行结果。
     */
    private List<SessionEvent> readEvents(String sessionId, long fromSeq, long toSeq) {
        Path eventsFile = sessionDir(sessionId).resolve("events.jsonl");
        if (!Files.exists(eventsFile)) {
            return List.of();
        }
        List<SessionEvent> events = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(eventsFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                var node = objectMapper.readTree(line);
                long seq = node.get("seq").asLong();
                if (seq < fromSeq || seq > toSeq) continue;
                events.add(deserializeEvent(node));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read events for session " + sessionId, e);
        }
        return events;
    }

    /**
     * 处理serialize 事件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param event 领域事件。
     * @return 转换后的目标对象。
     */
    private String serializeEvent(String sessionId, SessionEvent event) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("seq", event.seq());
            node.put("type", event.type().wireName());
            node.put("time", event.time().toString());

            // Payload serialization
            String payloadJson = serializePayload(event);
            if (payloadJson != null && !payloadJson.isBlank()) {
                node.set("payload", objectMapper.readTree(payloadJson));
            }

            // Surface intent
            SurfaceIntent intent = event.surfaceIntent();
            if (intent != null && intent.surfaceOp() != null) {
                if (intent.surfaceOp() instanceof SurfaceOp.Append) {
                    node.put("surfaceOp", "append");
                } else if (intent.surfaceOp() instanceof SurfaceOp.Replace r) {
                    node.put("surfaceOp", "replace:" + r.start() + ":" + r.end());
                }
                if (intent.sourceEventSeqs() != null && !intent.sourceEventSeqs().isEmpty()) {
                    node.put("sourceEventSeqs", intent.sourceEventSeqs().stream()
                            .map(String::valueOf).collect(Collectors.joining(",")));
                }
            }

            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    /**
     * 反序列化 事件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param node 策略树节点。
     * @return 方法执行结果。
     */
    private SessionEvent deserializeEvent(com.fasterxml.jackson.databind.JsonNode node) {
        long seq = node.get("seq").asLong();
        Instant time = Instant.parse(node.get("time").asText());
        SessionEventType type = SessionEventType.fromWireName(node.get("type").asText());

        String payloadJson = node.has("payload") ? node.get("payload").toString() : null;
        Object payload = deserializePayload(type, payloadJson);

        SurfaceIntent surfaceIntent = null;
        if (node.has("surfaceOp")) {
            String surfaceOpStr = node.get("surfaceOp").asText();
            SurfaceOp op;
            if ("append".equals(surfaceOpStr)) {
                op = new SurfaceOp.Append();
            } else if (surfaceOpStr.startsWith("replace:")) {
                String[] parts = surfaceOpStr.split(":");
                op = new SurfaceOp.Replace(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
            } else {
                op = null;
            }
            List<Long> seqs = null;
            if (node.has("sourceEventSeqs")) {
                seqs = java.util.Arrays.stream(node.get("sourceEventSeqs").asText().split(","))
                        .map(Long::parseLong).toList();
            }
            if (op != null) surfaceIntent = new SurfaceIntent(op, seqs);
        }

        return switch (type) {
            case TURN_START -> new SessionEvent.TurnStart(seq, time);
            case TURN_END -> {
                TurnEndReason reason = deserializeTurnEndReason(payloadJson);
                yield new SessionEvent.TurnEnd(seq, time, reason);
            }
            case STEP_START -> {
                StepStartPayload p = (StepStartPayload) payload;
                yield new SessionEvent.StepStart(seq, time, p.turn(), p.step());
            }
            case STEP_END -> {
                StepEndPayload p = (StepEndPayload) payload;
                yield new SessionEvent.StepEnd(seq, time, p.turn(), p.step());
            }
            case USER_MESSAGE -> new SessionEvent.UserMessage(seq, time, (UserMessagePayload) payload, surfaceIntent);
            case ASSISTANT_CHUNK -> new SessionEvent.AssistantChunk(seq, time, (AssistantChunkPayload) payload);
            case ASSISTANT_MESSAGE -> new SessionEvent.AssistantMessage(seq, time, (AssistantMessagePayload) payload, surfaceIntent);
            case TOOL_CALL -> new SessionEvent.ToolCall(seq, time, (ToolCallPayload) payload);
            case TOOL_RESULT -> new SessionEvent.ToolResult(seq, time, (ToolResultPayload) payload, surfaceIntent);
            case TODO_WRITE -> new SessionEvent.TodoWrite(seq, time, (TodoWritePayload) payload);
            case REQUEST_HEADER -> new SessionEvent.RequestHeader(seq, time, (RequestHeaderPayload) payload);
            case REQUEST_CONTEXT -> new SessionEvent.RequestContext(seq, time, (RequestContextPayload) payload);
            case SESSION_END_SEED -> new SessionEvent.SessionEndSeed(seq, time);
            case AGENT_INBOX_SPLICED -> new SessionEvent.AgentInboxSpliced(seq, time, (InboxSplicedPayload) payload);
            case PLAN_MODE_CHANGE -> new SessionEvent.PlanModeChange(seq, time, (PlanModePayload) payload);
            case TASK_SUBMITTED, TASK_AUTO_APPROVED, TASK_APPROVAL_REQUIRED,
                 TASK_APPROVED, TASK_EXECUTION_STARTED,
                 TASK_EXECUTION_COMPLETED, TASK_EXECUTION_FAILED,
                 AGENT_EVENT ->
                new SessionEvent.Generic(seq, time, type, payloadJson);
        };
    }

    /**
     * 处理serialize 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param event 领域事件。
     * @return 转换后的目标对象。
     */
    private String serializePayload(SessionEvent event) {
        try {
            if (event instanceof SessionEvent.TurnStart) return "{}";
            if (event instanceof SessionEvent.TurnEnd e) {
                return objectMapper.writeValueAsString(java.util.Map.of("reason", turnEndReasonToJson(e.reason())));
            }
            if (event instanceof SessionEvent.StepStart e) {
                return objectMapper.writeValueAsString(java.util.Map.of("turn", e.turn(), "step", e.step()));
            }
            if (event instanceof SessionEvent.StepEnd e) {
                return objectMapper.writeValueAsString(java.util.Map.of("turn", e.turn(), "step", e.step()));
            }
            if (event instanceof SessionEvent.Generic e) {
                return e.payload() == null ? "" : e.payload();
            }
            // For events with data() method
            try {
                var dataMethod = event.getClass().getMethod("data");
                Object data = dataMethod.invoke(event);
                if (data != null) {
                    return objectMapper.writeValueAsString(data);
                }
            } catch (NoSuchMethodException ignored) {}
            return "{}";
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    /**
     * 反序列化 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param type 类型参数。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private Object deserializePayload(SessionEventType type, String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return null;
        try {
            return switch (type) {
                case STEP_START -> objectMapper.readValue(json, StepStartPayload.class);
                case STEP_END -> objectMapper.readValue(json, StepEndPayload.class);
                case USER_MESSAGE -> deserializeUserMessagePayload(json);
                case ASSISTANT_CHUNK -> objectMapper.readValue(json, AssistantChunkPayload.class);
                case ASSISTANT_MESSAGE -> deserializeAssistantMessagePayload(json);
                case TOOL_CALL -> objectMapper.readValue(json, ToolCallPayload.class);
                case TOOL_RESULT -> deserializeToolResultPayload(json);
                case TODO_WRITE -> objectMapper.readValue(json, TodoWritePayload.class);
                case REQUEST_HEADER -> objectMapper.readValue(json, RequestHeaderPayload.class);
                case REQUEST_CONTEXT -> objectMapper.readValue(json, RequestContextPayload.class);
                case AGENT_INBOX_SPLICED -> deserializeInboxSplicedPayload(json);
                case PLAN_MODE_CHANGE -> objectMapper.readValue(json, PlanModePayload.class);
                default -> null;
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize payload for type " + type, e);
        }
    }

    /**
     * 反序列化 用户 消息 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private UserMessagePayload deserializeUserMessagePayload(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new UserMessagePayload(
                    node.path("turn").asLong(0),
                    node.path("step").asLong(0),
                    deserializeMessage(node.path("message"))
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize user message payload", e);
        }
    }

    /**
     * 反序列化 Assistant 消息 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private AssistantMessagePayload deserializeAssistantMessagePayload(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            TokenUsage usage = node.hasNonNull("usage")
                    ? objectMapper.treeToValue(node.get("usage"), TokenUsage.class)
                    : null;
            return new AssistantMessagePayload(
                    node.path("turn").asLong(0),
                    node.path("step").asLong(0),
                    deserializeMessage(node.path("message")),
                    usage
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize assistant message payload", e);
        }
    }

    /**
     * 反序列化 工具 结果 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private ToolResultPayload deserializeToolResultPayload(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            java.util.Map<String, Object> error = node.hasNonNull("error")
                    ? objectMapper.convertValue(node.get("error"), java.util.Map.class)
                    : null;
            java.util.Map<String, Object> meta = node.hasNonNull("meta")
                    ? objectMapper.convertValue(node.get("meta"), java.util.Map.class)
                    : null;
            return new ToolResultPayload(
                    node.path("turn").asLong(0),
                    node.path("step").asLong(0),
                    deserializeMessage(node.path("message")),
                    error,
                    meta
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize tool result payload", e);
        }
    }

    /**
     * 反序列化 Inbox Spliced 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private InboxSplicedPayload deserializeInboxSplicedPayload(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            List<Message> inserted = new ArrayList<>();
            JsonNode insertedNode = node.path("inserted");
            if (insertedNode.isArray()) {
                for (JsonNode messageNode : insertedNode) {
                    Message message = deserializeMessage(messageNode);
                    if (message != null) {
                        inserted.add(message);
                    }
                }
            }
            return new InboxSplicedPayload(
                    node.path("target").asText(null),
                    node.path("start").asInt(0),
                    node.path("removedCount").asInt(0),
                    inserted,
                    node.path("outcome").isMissingNode() || node.path("outcome").isNull() ? null : node.path("outcome").asText(null)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize inbox spliced payload", e);
        }
    }

    /**
     * 反序列化 消息。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param node 策略树节点。
     * @return 方法执行结果。
     */
    private Message deserializeMessage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String id = node.path("id").asText(null);
        String role = node.path("role").asText(null);
        MessageSource source = deserializeMessageSource(node.path("source"), role);
        List<ContentBlock> content = new ArrayList<>();
        JsonNode contentNode = node.path("content");
        if (contentNode.isArray()) {
            for (JsonNode blockNode : contentNode) {
                ContentBlock block = deserializeContentBlock(blockNode);
                if (block != null) {
                    content.add(block);
                }
            }
        }
        return new Message(id, role, content, source);
    }

    /**
     * 反序列化 消息 来源。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param node 策略树节点。
     * @param role role参数。
     * @return 方法执行结果。
     */
    private MessageSource deserializeMessageSource(JsonNode node, String role) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return switch (role == null ? "" : role) {
                case "assistant" -> new MessageSource.ModelMessageSource("unknown", "unknown");
                case "tool" -> new MessageSource.ToolMessageSource("unknown");
                case "user" -> new MessageSource.UserSource();
                default -> new MessageSource.UserSource();
            };
        }
        String kind = node.path("kind").asText("");
        if (kind.equals("user") || "user".equals(role)) {
            return new MessageSource.UserSource();
        }
        if (kind.equals("tool") || "tool".equals(role)) {
            return new MessageSource.ToolMessageSource(node.path("callId").asText("unknown"));
        }
        if (kind.equals("model") || "assistant".equals(role)) {
            return new MessageSource.ModelMessageSource(
                    node.path("provider").asText("unknown"),
                    node.path("model").asText("unknown")
            );
        }
        return new MessageSource.UserSource();
    }

    /**
     * 反序列化 Content Block。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param node 策略树节点。
     * @return 方法执行结果。
     */
    private ContentBlock deserializeContentBlock(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.has("text") && node.size() == 1) {
            return new TextBlock(node.get("text").asText(""));
        }
        if (node.has("text") && !node.has("toolCallId") && !node.has("attachment")) {
            return new TextBlock(node.get("text").asText(""));
        }
        if (node.has("toolCallId")) {
            List<ContentBlock> nested = new ArrayList<>();
            JsonNode contentNode = node.path("content");
            if (contentNode.isArray()) {
                for (JsonNode child : contentNode) {
                    ContentBlock block = deserializeContentBlock(child);
                    if (block != null) nested.add(block);
                }
            }
            return new ToolResultBlock(
                    node.path("toolCallId").asText(""),
                    nested,
                    node.path("isError").asBoolean(false)
            );
        }
        if (node.has("id") && node.has("name") && node.has("arguments")) {
            return new ToolCallBlock(node.path("id").asText(""), node.path("name").asText(""), node.path("arguments").asText(""));
        }
        if (node.has("text") && node.has("reasoning")) {
            return new ReasoningBlock(node.path("text").asText(""));
        }
        return null;
    }

    /**
     * 反序列化 Turn End Reason。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private TurnEndReason deserializeTurnEndReason(String json) {
        try {
            if (json == null || json.isBlank()) return new TurnEndReason.Error("missing", "UNKNOWN");
            var node = objectMapper.readTree(json).get("reason");
            if (node == null) return new TurnEndReason.Error("missing reason", "UNKNOWN");
            String kind = node.has("type") ? node.get("type").asText() : "Completed";
            return switch (kind) {
                case "Completed" -> new TurnEndReason.Completed();
                case "Aborted" -> new TurnEndReason.Aborted(node.has("reason") ? node.get("reason").asText() : "");
                case "Blocked" -> new TurnEndReason.Blocked();
                case "Error" -> new TurnEndReason.Error(
                        node.has("message") ? node.get("message").asText() : "",
                        node.has("code") ? node.get("code").asText() : "UNKNOWN");
                case "MaxTokens" -> new TurnEndReason.MaxTokens();
                case "Interrupted" -> new TurnEndReason.Interrupted();
                default -> new TurnEndReason.Error("unknown: " + kind, "UNKNOWN");
            };
        } catch (Exception e) {
            return new TurnEndReason.Error("deserialize error: " + e.getMessage(), "UNKNOWN");
        }
    }

    /**
     * 处理turn End Reason To JSON。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param reason reason参数。
     * @return 方法执行结果。
     */
    private java.util.Map<String, Object> turnEndReasonToJson(TurnEndReason reason) {
        if (reason instanceof TurnEndReason.Completed) return java.util.Map.of("type", "Completed");
        if (reason instanceof TurnEndReason.Aborted r) return java.util.Map.of("type", "Aborted", "reason", r.reason());
        if (reason instanceof TurnEndReason.Blocked) return java.util.Map.of("type", "Blocked");
        if (reason instanceof TurnEndReason.Error r) return java.util.Map.of("type", "Error", "message", r.message(), "code", r.code());
        if (reason instanceof TurnEndReason.MaxTokens) return java.util.Map.of("type", "MaxTokens");
        if (reason instanceof TurnEndReason.Interrupted) return java.util.Map.of("type", "Interrupted");
        throw new IllegalStateException("Unknown TurnEndReason: " + reason.getClass());
    }

    /**
     * 处理会话 Dir。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */
    private Path sessionDir(String sessionId) {
        // Sanitize sessionId to prevent path traversal
        String safe = sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return baseDir.resolve(safe);
    }
}
