package net.linlan.dsh.domain.session.conversation.service;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ReasoningBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolResultBlock;
import net.linlan.dsh.domain.session.IConversationQueryService;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationMessageVO;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationSessionDetailVO;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationSessionSummaryVO;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.log.adapter.repository.ISessionEventRepository;
import net.linlan.dsh.domain.session.log.model.entity.SessionEventEntity;
import net.linlan.dsh.domain.session.registry.adapter.repository.IHarnessSessionRepository;
import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * 会话查询服务。
 * <p>
 * 执行流程：
 * 1. 从会话仓储和事件仓储读取原始数据；
 * 2. 过滤并装配出侧边栏摘要、消息列表和详情；
 * 3. 返回 UI 可直接消费的读模型。
 * <p>
 * 双轨合并：Agent 会话（harness_session_event_log）与任务会话
 * （harness_session + harness_session_event）都会被聚合展示。
 *
 *
 */
@Service
public class ConversationQueryService implements IConversationQueryService {

    private final IHarnessSessionRepository harnessSessionRepository;
    private final ISessionEventRepository sessionEventRepository;
    private final ISessionEventStore sessionEventStore;

    /**
     * 初始化会话查询服务。
     * <p>流程：读取会话仓储和事件日志 → 拼装会话与消息视图。</p>
     */
    public ConversationQueryService(
            IHarnessSessionRepository harnessSessionRepository,
            ISessionEventRepository sessionEventRepository,
            ISessionEventStore sessionEventStore
    ) {
        this.harnessSessionRepository = harnessSessionRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.sessionEventStore = sessionEventStore;
    }

    /**
     * 查询最近会话。
     * <p>
     * 执行流程：
     * 1. 从旧会话表（harness_session）读取任务会话；
     * 2. 从新事件表（harness_session_header）读取 Agent 会话；
     * 3. 按创建时间倒序合并去重，装配摘要视图并返回。
     */
    @Override
    public List<ConversationSessionSummaryVO> listRecentSessions(int limit, int offset) {
        Map<String, ConversationSessionSummaryVO> byId = new LinkedHashMap<>();
        int fetchLimit = Math.max(offset, 0) + Math.max(limit, 0);
        // 旧链路：任务会话
        for (HarnessSessionAggregate session : harnessSessionRepository.listRecent(fetchLimit)) {
            byId.put(session.getSessionId(), toSummary(session));
        }
        // 新链路：Agent 会话（以 agentPreset 为 agentId 还原侧边栏标识）
        for (SessionHeader header : sessionEventStore.listHeaders(fetchLimit)) {
            byId.computeIfAbsent(header.sessionId(), sid -> toSummaryFromHeader(header));
        }
        List<ConversationSessionSummaryVO> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(ConversationSessionSummaryVO::createdAt).reversed());
        return merged.stream()
                .skip(Math.max(offset, 0))
                .limit(Math.max(limit, 0))
                .toList();
    }

    /**
     * 查询会话消息。
     * <p>
     * 执行流程：
     * 1. 优先从新事件表读取（Agent 会话）；
     * 2. 若无则回退到旧事件表（任务会话）；
     * 3. 过滤可见消息并转换为消息 VO。
     */
    @Override
    public List<ConversationMessageVO> listMessages(String sessionId) {
        List<SessionEvent> typed = sessionEventStore.readAll(sessionId);
        if (!typed.isEmpty()) {
            List<ConversationMessageVO> messages = new ArrayList<>();
            Map<String, ConversationMessageVO> toolCalls = new LinkedHashMap<>();
            return typed.stream()
                    .map(event -> toMessageFromEvent(event, messages, toolCalls))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return sessionEventRepository.queryBySessionId(sessionId).stream()
                .filter(this::isVisibleMessage)
                .map(this::toMessage)
                .toList();
    }

    /**
     * 分页查询会话摘要。
     * <p>
     * 执行流程：
     * 1. 根据 workspaceId 读取会话集合；
     * 2. 按 offset 和 limit 截取；
     * 3. 返回管理员视角摘要列表。
     */
    @Override
    public List<ConversationSessionSummaryVO> querySessions(String workspaceId, int offset, int limit) {
        List<HarnessSessionAggregate> sessions = harnessSessionRepository.listRecent(limit);
        return sessions.stream()
                .filter(s -> workspaceId == null || workspaceId.equals(s.getWorkspaceId()))
                .skip(offset)
                .limit(limit)
                .map(this::toSummaryWithAdmin)
                .toList();
    }

    /**
     * 查询会话详情。
     * <p>
     * 执行流程：
     * 1. 根据 sessionId 读取会话；
     * 2. 组装会话基本信息和消息列表；
     * 3. 返回完整详情对象。
     */
    @Override
    public ConversationSessionDetailVO querySessionDetail(String sessionId) {
        HarnessSessionAggregate session = harnessSessionRepository.findBySessionId(sessionId).orElse(null);
        if (session == null) return null;
        List<ConversationMessageVO> messages = listMessages(sessionId);
        return new ConversationSessionDetailVO(
                session.getSessionId(),
                session.getWorkspaceId(),
                session.getStatus().name(),
                session.getModelCode(),
                session.getProfileCode(),
                session.getCreatedAt(),
                session.getLastActivityAt(),
                messages
        );
    }

    /**
     * 剥离 hidden-context 标签并提取用户可见文本。
     * <p>执行流程：移除 <hidden-context>...</hidden-context> 及未闭合的 <hidden-context>... 残片 ->
     * 若文本中包含"用户原始请求："标记，则截取标记之后的内容。
     */
    private String visibleUserText(String content) {
        if (content == null || content.isBlank()) return "";
        String text = content.replaceAll("(?s)<hidden-context>.*?</hidden-context>", "")
                .replaceAll("(?s)<hidden-context>.*$", "").trim();
        int marker = text.lastIndexOf("用户原始请求：");
        if (marker >= 0) {
            text = text.substring(marker + "用户原始请求：".length()).trim();
        }
        return text;
    }

    /**
     * 生成预览文本：优先使用可见用户文本，不可见时回退到原始内容。
     */
    private String previewText(String content) {
        String visible = visibleUserText(content);
        return visible.isBlank() ? (content == null ? "" : content.trim()) : visible;
    }

    /**
     * 装配会话摘要。
     * <p>
     * 执行流程：
     * 1. 读取会话消息；
     * 2. 取首条用户消息作为标题、末条消息作为摘要；
     * 3. 组装摘要读模型。
     */
    private ConversationSessionSummaryVO toSummary(HarnessSessionAggregate session) {
        List<ConversationMessageVO> messages = listMessages(session.getSessionId());
        String title = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .findFirst()
                .map(message -> abbreviate(visibleUserText(message.content()), 16))
                .filter(text -> text != null && !text.isBlank())
                .orElse("新会话");
        String preview = messages.isEmpty() ? "等待消息" : abbreviate(previewText(messages.get(messages.size() - 1).content()), 24);
        return new ConversationSessionSummaryVO(
                session.getSessionId(),
                title,
                preview,
                session.getStatus().name(),
                session.getCreatedAt()
        );
    }

    /**
     * 装配管理员视角会话摘要。
     * <p>
     * 执行流程：
     * 1. 读取会话消息；
     * 2. 生成标题、摘要和额外元数据；
     * 3. 返回管理员侧读模型。
     */
    private ConversationSessionSummaryVO toSummaryWithAdmin(HarnessSessionAggregate session) {
        List<ConversationMessageVO> messages = listMessages(session.getSessionId());
        String title = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .findFirst()
                .map(message -> abbreviate(visibleUserText(message.content()), 16))
                .filter(text -> text != null && !text.isBlank())
                .orElse("新会话");
        String preview = messages.isEmpty() ? "等待消息" : abbreviate(previewText(messages.get(messages.size() - 1).content()), 24);
        return new ConversationSessionSummaryVO(
                session.getSessionId(),
                title,
                preview,
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getWorkspaceId(),
                session.getModelCode(),
                session.getProfileCode(),
                session.getLastActivityAt()
        );
    }

    /**
     * 判断Visible Message是否成立。
     */
    private boolean isVisibleMessage(SessionEventEntity event) {
        return "user.message".equals(event.eventType()) || "assistant.message".equals(event.eventType());
    }

    /**
     * 转换为 消息。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param event 领域事件。
     * @return 转换后的目标对象。
     */
    private ConversationMessageVO toMessage(SessionEventEntity event) {
        String role = event.eventType().startsWith("user") ? "user" : "assistant";
        return new ConversationMessageVO(role, event.payload(), event.occurredAt());
    }

    /**
     * 装配新事件表会话摘要（Agent 会话）。
     * <p>
     * 执行流程：
     * 1. 从事件表读取全部事件；
     * 2. 取首条 user/message 文本作为标题、末条 user/assistant 文本作为摘要；
     * 3. 组装摘要读模型，agentId 从 agentPreset 还原（AgentRunFactory 创建时写入）。
     */
    private ConversationSessionSummaryVO toSummaryFromHeader(SessionHeader header) {
        List<SessionEvent> events = sessionEventStore.readAll(header.sessionId());
        String title = null;
        String preview = null;
        for (SessionEvent event : events) {
            String text = null;
            boolean isUser = false;
            if (event instanceof SessionEvent.UserMessage um) {
                text = extractText(um.data().message());
                isUser = true;
            } else if (event instanceof SessionEvent.AssistantMessage am) {
                text = extractText(am.data().message());
            }
            if (text == null || text.isBlank()) continue;
            if (isUser && title == null) {
                String candidate = abbreviate(visibleUserText(text), 16);
                if (candidate != null && !candidate.isBlank()) {
                    title = candidate;
                }
            }
            preview = abbreviate(previewText(text), 24);
        }
        return new ConversationSessionSummaryVO(
                header.sessionId(),
                title != null && !title.isBlank() ? title : "新会话",
                preview != null && !preview.isBlank() ? preview : "等待消息",
                "COMPLETED",
                header.createdAt(),
                header.cwd(),
                null,
                null,
                null,
                header.agentPreset()
        );
    }

    /**
     * 将新事件表的单条事件转换为消息 VO。
     * 只暴露 user/message 与 assistant/message，其余事件返回 null 被过滤。
     */
    private ConversationMessageVO toMessageFromEvent(
            SessionEvent event,
            List<ConversationMessageVO> messages,
            Map<String, ConversationMessageVO> toolCalls
    ) {
        if (event instanceof SessionEvent.UserMessage um) {
            return new ConversationMessageVO("user", extractText(um.data().message()), extractBlocks(um.data().message()), event.time());
        } else if (event instanceof SessionEvent.AssistantMessage am) {
            ConversationMessageVO message = new ConversationMessageVO(
                    "assistant", extractText(am.data().message()), extractBlocks(am.data().message()), event.time());
            messages.add(message);
            return message;
        } else if (event instanceof SessionEvent.ToolCall tc) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("kind", "tool-call");
            model.put("callId", tc.data().callId());
            model.put("toolName", tc.data().name());
            model.put("argsRaw", tc.data().arguments());
            ConversationMessageVO message = new ConversationMessageVO(
                    "tool", "", List.of(model), event.time());
            messages.add(message);
            toolCalls.put(tc.data().callId(), message);
            return message;
        } else if (event instanceof SessionEvent.ToolResult tr && tr.data().message() != null) {
            String callId = null;
            if (tr.data().message().source() instanceof MessageSource.ToolMessageSource source) {
                callId = source.callId();
            }
            ConversationMessageVO call = callId == null ? null : toolCalls.get(callId);
            if (call != null) {
                Map<String, Object> model = new LinkedHashMap<>(call.blocks().get(0));
                model.put("status", tr.data().error() != null ? "error" : "success");
                model.put("result", extractToolResultText(tr.data().message().content()));
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractBlocks(Message message) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (message == null) return blocks;
        for (var block : message.content()) {
            Map<String, Object> model = new LinkedHashMap<>();
            if (block instanceof TextBlock tb) {
                model.put("kind", "text");
                model.put("text", tb.text());
                blocks.add(model);
            } else if (block instanceof ReasoningBlock rb) {
                model.put("kind", "reasoning");
                model.put("text", rb.text());
                blocks.add(model);
            }
        }
        return blocks;
    }

    private String extractToolResultText(List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof ToolResultBlock result) {
                for (ContentBlock inner : result.content()) {
                    if (inner instanceof TextBlock tb) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(tb.text());
                    }
                }
            } else if (block instanceof TextBlock tb) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(tb.text());
            }
        }
        return sb.toString();
    }

    /**
     * 提取 Text。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param message 消息内容或对象。
     * @return 方法执行结果。
     */
    private String extractText(Message message) {
        if (message == null || message.content() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var block : message.content()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            }
        }
        return sb.toString();
    }

    /**
     * 处理abbreviate。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param text text参数。
     * @param maxLength max Length参数。
     * @return 方法执行结果。
     */
    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
