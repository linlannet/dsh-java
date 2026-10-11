package net.linlan.dsh.infrastructure.adapter.eventstore;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ReasoningBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.model.valobj.ToolResultBlock;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.infrastructure.dao.ISessionEventRowDao;
import net.linlan.dsh.infrastructure.dao.po.SessionEventRowPO;
import net.linlan.dsh.infrastructure.dao.po.SessionHeaderRowPO;
import net.linlan.dsh.domain.session.event.model.valobj.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed implementation of {@link SessionPersistenceSeam}.
 * <p>
 * Persists typed session events and headers via MyBatis DAO.
 * Payloads are serialized as JSON.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class JdbcSessionEventStore implements SessionPersistenceSeam {

    private final ISessionEventRowDao dao;
    private final ObjectMapper objectMapper;

    /**
     * 处理Jdbc 会话 事件 Store。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param dao dao参数。
     * @return 方法执行结果。
     */
    public JdbcSessionEventStore(ISessionEventRowDao dao) {
        this.dao = dao;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // for Instant support
    }

    @Override
    @Transactional
    public void saveHeader(SessionHeader header) {
        dao.upsertHeader(
                header.sessionId(),
                header.version(),
                header.createdAt(),
                header.cwd(),
                header.parentSession(),
                header.seedLength(),
                header.origin(),
                header.delegationDepth(),
                header.agentPreset()
        );
    }
    /**
     * 获取Header。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 获取到的Header。
     */


    @Override
    public Optional<SessionHeader> getHeader(String sessionId) {
        SessionHeaderRowPO row = dao.getHeader(sessionId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionHeader(
                row.version(),
                row.sessionId(),
                row.createdAt(),
                row.cwd(),
                row.parentSession(),
                row.seedLength(),
                row.origin(),
                row.delegationDepth(),
                row.agentPreset()
        ));
    }
    /**
     * 追加目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param event 领域事件。
     */


    @Override
    @Transactional
    public void append(String sessionId, SessionEvent event) {
        dao.insert(toRow(sessionId, event));
    }
    /**
     * 追加Batch。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param events events参数。
     */


    @Override
    @Transactional
    public void appendBatch(String sessionId, List<SessionEvent> events) {
        List<SessionEventRowPO> rows = events.stream()
                .map(event -> toRow(sessionId, event))
                .toList();
        dao.insertBatch(rows);
    }
    /**
     * 读取All。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> readAll(String sessionId) {
        return dao.queryBySessionId(sessionId).stream()
                .map(this::toEvent)
                .toList();
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
        return dao.queryAfter(sessionId, afterSeq).stream()
                .map(this::toEvent)
                .toList();
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
        return dao.queryRange(sessionId, fromSeq, toSeq).stream()
                .map(this::toEvent)
                .toList();
    }
    /**
     * 获取Last Seq。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 获取到的Last Seq。
     */


    @Override
    public long getLastSeq(String sessionId) {
        Long result = dao.getMaxSeq(sessionId);
        return result == null ? -1 : result;
    }
    /**
     * count。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public long count(String sessionId) {
        return dao.count(sessionId);
    }
    /**
     * 列出Headers。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param limit limit参数。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionHeader> listHeaders(int limit) {
        return dao.listHeaders(limit).stream()
                .map(row -> new SessionHeader(
                        row.version(),
                        row.sessionId(),
                        row.createdAt(),
                        row.cwd(),
                        row.parentSession(),
                        row.seedLength(),
                        row.origin(),
                        row.delegationDepth(),
                        row.agentPreset()
                ))
                .toList();
    }

    // ── Serialization ────────────────────────────────────────────

    /**
     * 转换为 Row。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @param event 领域事件。
     * @return 转换后的目标对象。
     */
    private SessionEventRowPO toRow(String sessionId, SessionEvent event) {
        String payloadJson = serializePayload(event);
        String surfaceOp = serializeSurfaceOp(event);
        String sourceSeqs = serializeSourceSeqs(event);
        return new SessionEventRowPO(
                sessionId,
                event.seq(),
                event.type().wireName(),
                event.time(),
                payloadJson,
                surfaceOp,
                sourceSeqs
        );
    }

    /**
     * 转换为 事件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param row row参数。
     * @return 转换后的目标对象。
     */
    private SessionEvent toEvent(SessionEventRowPO row) {
        SessionEventType type = SessionEventType.fromWireName(row.eventType());
        Object payload = deserializePayload(type, row.payloadJson());
        SurfaceIntent surfaceIntent = deserializeSurfaceIntent(row.surfaceOp(), row.sourceEventSeqs());

        return switch (type) {
            case TURN_START -> new SessionEvent.TurnStart(row.seq(), row.eventTime());
            case TURN_END -> {
                TurnEndReason reason = deserializeTurnEndReason(row.payloadJson());
                yield new SessionEvent.TurnEnd(row.seq(), row.eventTime(), reason);
            }
            case STEP_START -> {
                StepStartPayload p = (StepStartPayload) payload;
                yield new SessionEvent.StepStart(row.seq(), row.eventTime(), p.turn(), p.step());
            }
            case STEP_END -> {
                StepEndPayload p = (StepEndPayload) payload;
                yield new SessionEvent.StepEnd(row.seq(), row.eventTime(), p.turn(), p.step());
            }
            case USER_MESSAGE -> new SessionEvent.UserMessage(row.seq(), row.eventTime(), (UserMessagePayload) payload, surfaceIntent);
            case ASSISTANT_CHUNK -> new SessionEvent.AssistantChunk(row.seq(), row.eventTime(), (AssistantChunkPayload) payload);
            case ASSISTANT_MESSAGE -> new SessionEvent.AssistantMessage(row.seq(), row.eventTime(), (AssistantMessagePayload) payload, surfaceIntent);
            case TOOL_CALL -> new SessionEvent.ToolCall(row.seq(), row.eventTime(), (ToolCallPayload) payload);
            case TOOL_RESULT -> new SessionEvent.ToolResult(row.seq(), row.eventTime(), (ToolResultPayload) payload, surfaceIntent);
            case TODO_WRITE -> new SessionEvent.TodoWrite(row.seq(), row.eventTime(), (TodoWritePayload) payload);
            case REQUEST_HEADER -> new SessionEvent.RequestHeader(row.seq(), row.eventTime(), (RequestHeaderPayload) payload);
            case REQUEST_CONTEXT -> new SessionEvent.RequestContext(row.seq(), row.eventTime(), (RequestContextPayload) payload);
            case SESSION_END_SEED -> new SessionEvent.SessionEndSeed(row.seq(), row.eventTime());
            case AGENT_INBOX_SPLICED -> new SessionEvent.AgentInboxSpliced(row.seq(), row.eventTime(), (InboxSplicedPayload) payload);
            case PLAN_MODE_CHANGE -> new SessionEvent.PlanModeChange(row.seq(), row.eventTime(), (PlanModePayload) payload);
            case TASK_SUBMITTED, TASK_AUTO_APPROVED, TASK_APPROVAL_REQUIRED,
                 TASK_APPROVED, TASK_EXECUTION_STARTED,
                 TASK_EXECUTION_COMPLETED, TASK_EXECUTION_FAILED,
                 AGENT_EVENT ->
                new SessionEvent.Generic(row.seq(), row.eventTime(), type, row.payloadJson());
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
            if (event instanceof SessionEvent.TurnStart) {
            return objectMapper.writeValueAsString(java.util.Map.of());
        } else if (event instanceof SessionEvent.TurnEnd e) {
            return objectMapper.writeValueAsString(java.util.Map.of("reason", turnEndReasonToJson(e.reason())));
        } else if (event instanceof SessionEvent.StepStart e) {
            return objectMapper.writeValueAsString(java.util.Map.of("turn", e.turn(), "step", e.step()));
        } else if (event instanceof SessionEvent.StepEnd e) {
            return objectMapper.writeValueAsString(java.util.Map.of("turn", e.turn(), "step", e.step()));
        } else if (event instanceof SessionEvent.UserMessage e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.AssistantChunk e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.AssistantMessage e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.ToolCall e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.ToolResult e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.TodoWrite e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.RequestHeader e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.RequestContext e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.SessionEndSeed) {
            return objectMapper.writeValueAsString(java.util.Map.of());
        } else if (event instanceof SessionEvent.AgentInboxSpliced e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.PlanModeChange e) {
            return objectMapper.writeValueAsString(e.data());
        } else if (event instanceof SessionEvent.Generic e) {
            return e.payload() == null ? "" : e.payload();
        } else {
            throw new IllegalStateException("Unknown event variant: " + event.getClass());
        }
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize event payload", ex);
        }
    }

    /**
     * 处理serialize Surface Op。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param event 领域事件。
     * @return 转换后的目标对象。
     */
    private String serializeSurfaceOp(SessionEvent event) {
        SurfaceIntent intent = event.surfaceIntent();
        if (intent == null || intent.surfaceOp() == null) {
            return null;
        }
        if (intent.surfaceOp() instanceof SurfaceOp.Append) {
            return "append";
        } else if (intent.surfaceOp() instanceof SurfaceOp.Replace r) {
            return "replace:" + r.start() + ":" + r.end();
        } else {
            return null;
        }
    }

    /**
     * 处理serialize 来源 Seqs。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param event 领域事件。
     * @return 转换后的目标对象。
     */
    private String serializeSourceSeqs(SessionEvent event) {
        SurfaceIntent intent = event.surfaceIntent();
        if (intent == null || intent.sourceEventSeqs() == null || intent.sourceEventSeqs().isEmpty()) {
            return null;
        }
        return intent.sourceEventSeqs().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * 反序列化 Surface Intent。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param surfaceOpStr surface Op Str参数。
     * @param sourceSeqsStr 来源 Seqs Str参数。
     * @return 方法执行结果。
     */
    private SurfaceIntent deserializeSurfaceIntent(String surfaceOpStr, String sourceSeqsStr) {
        if (surfaceOpStr == null || surfaceOpStr.isBlank()) {
            return null;
        }
        SurfaceOp op;
        if ("append".equals(surfaceOpStr)) {
            op = new SurfaceOp.Append();
        } else if (surfaceOpStr.startsWith("replace:")) {
            String[] parts = surfaceOpStr.split(":");
            op = new SurfaceOp.Replace(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } else {
            return null;
        }
        List<Long> seqs = null;
        if (sourceSeqsStr != null && !sourceSeqsStr.isBlank()) {
            seqs = java.util.Arrays.stream(sourceSeqsStr.split(","))
                    .map(Long::parseLong)
                    .toList();
        }
        return new SurfaceIntent(op, seqs);
    }

    /**
     * 反序列化 载荷。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param type 类型参数。
     * @param json JSON参数。
     * @return 方法执行结果。
     */
    private Object deserializePayload(SessionEventType type, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
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
                default -> null;
            };
        } catch (JsonProcessingException e) {
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
        if (kind.equals("user") || role.equals("user")) {
            return new MessageSource.UserSource();
        }
        if (kind.equals("tool") || role.equals("tool")) {
            return new MessageSource.ToolMessageSource(node.path("callId").asText("unknown"));
        }
        if (kind.equals("model") || role.equals("assistant")) {
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
            var node = objectMapper.readTree(json);
            var reasonNode = node.get("reason");
            if (reasonNode == null) {
                return new TurnEndReason.Error("unknown", "UNKNOWN");
            }
            var kindNode = reasonNode.get("type");
            String kind = kindNode != null ? kindNode.asText() : "Completed";
            return switch (kind) {
                case "Completed" -> new TurnEndReason.Completed();
                case "Aborted" -> new TurnEndReason.Aborted(reasonNode.has("reason") ? reasonNode.get("reason").asText() : "");
                case "Blocked" -> new TurnEndReason.Blocked();
                case "Error" -> new TurnEndReason.Error(
                        reasonNode.has("message") ? reasonNode.get("message").asText() : "",
                        reasonNode.has("code") ? reasonNode.get("code").asText() : "UNKNOWN");
                case "MaxTokens" -> new TurnEndReason.MaxTokens();
                case "Interrupted" -> new TurnEndReason.Interrupted();
                default -> new TurnEndReason.Error("unknown reason: " + kind, "UNKNOWN");
            };
        } catch (Exception e) {
            return new TurnEndReason.Error("failed to deserialize: " + e.getMessage(), "UNKNOWN");
        }
    }

    /**
     * 处理turn End Reason To JSON。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param reason reason参数。
     * @return 方法执行结果。
     */
    private java.util.Map<String, Object> turnEndReasonToJson(TurnEndReason reason) {
        if (reason instanceof TurnEndReason.Completed) {
            return java.util.Map.of("type", "Completed");
        } else if (reason instanceof TurnEndReason.Aborted r) {
            return java.util.Map.of("type", "Aborted", "reason", r.reason());
        } else if (reason instanceof TurnEndReason.Blocked) {
            return java.util.Map.of("type", "Blocked");
        } else if (reason instanceof TurnEndReason.Error r) {
            return java.util.Map.of("type", "Error", "message", r.message(), "code", r.code());
        } else if (reason instanceof TurnEndReason.MaxTokens) {
            return java.util.Map.of("type", "MaxTokens");
        } else if (reason instanceof TurnEndReason.Interrupted) {
            return java.util.Map.of("type", "Interrupted");
        } else {
            throw new IllegalStateException("Unknown TurnEndReason: " + reason.getClass());
        }
    }
}
