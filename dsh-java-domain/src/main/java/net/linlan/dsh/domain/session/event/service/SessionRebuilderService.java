package net.linlan.dsh.domain.session.event.service;

import net.linlan.dsh.domain.agent.model.entity.Phase;
import net.linlan.dsh.domain.agent.service.Inbox;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionProjectionCache;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.RequestHeaderPayload;
import net.linlan.dsh.domain.session.event.model.valobj.RequestContextPayload;
import net.linlan.dsh.domain.session.event.model.valobj.TodoWritePayload;
import net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason;
import net.linlan.dsh.domain.session.event.service.migration.MigrationCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * SessionRebuilderService 封装会话SessionRebuilder的领域规则与流程。
 */
@Service
public class SessionRebuilderService {

    private final ISessionEventLogService eventLogService;
    private final ISessionProjectionCache projectionCache;
    private volatile MigrationCatalog migrationCatalog;

    /**
     * 处理会话 Rebuilder Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param eventLogService 事件 Log Service参数。
     * @return 领域处理结果。
     */
    public SessionRebuilderService(
            ISessionEventLogService eventLogService,
            ISessionProjectionCache projectionCache
    ) {
        this.eventLogService = eventLogService;
        this.projectionCache = projectionCache;
        this.migrationCatalog = MigrationCatalog.defaultChain();
    }

    /**
     * 注入自定义迁移链。
     */
    public void setMigrationCatalog(MigrationCatalog catalog) {
        this.migrationCatalog = java.util.Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * 处理rebuild。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    public RestoredSession rebuild(String sessionId) {
        long lastSeq = eventLogService.getLastSeq(sessionId);
        return projectionCache.get(sessionId, lastSeq, RestoredSession.class)
                .orElseGet(() -> rebuildAndCache(sessionId, lastSeq));
    }

    private RestoredSession rebuildAndCache(String sessionId, long lastSeq) {
        eventLogService.restore(sessionId);

        SessionHeader header = eventLogService.getHeader(sessionId);
        List<SessionEvent> events = eventLogService.readAll(sessionId);

        SessionLog rawLog = new SessionLog(sessionId, header);
        if (!events.isEmpty()) {
            rawLog.appendBatch(events);
        }
        SessionLog migratedLog = migrationCatalog.migrateToCurrent(rawLog);
        header = migratedLog.header();
        events = migratedLog.events();

        // 2. Derive messages (surface projection)
        // SessionLog.deriveMessages() does the surface projection
        // We need to get the restored SessionLog — but it's internal to EventLogService.
        // Instead, we project from the raw events.
        List<Message> messages = projectMessages(events);

        // 3. Determine agent phase
        Phase phase = derivePhase(events);

        // 4. Extract latest todo snapshot
        Optional<TodoWritePayload> todos = extractLatestTodo(events);

        // 5. Extract latest request header
        Optional<RequestHeaderPayload> requestHeader = extractLatestRequestHeader(events);

        // 6. Extract latest request context
        Optional<RequestContextPayload> requestContext = extractLatestRequestContext(events);

        // 7. Count turns and steps
        int turnCount = countTurns(events);
        int stepCount = countSteps(events);

        // 8. Last turn end reason
        Optional<TurnEndReason> lastTurnEndReason = extractLastTurnEndReason(events);

        // 9. Last assistant message
        Optional<Message> lastAssistantMessage = extractLastAssistantMessage(events);

        RestoredSession restored = new RestoredSession(
                header,
                messages,
                phase,
                todos.orElse(null),
                requestHeader.orElse(null),
                requestContext.orElse(null),
                turnCount,
                stepCount,
                lastTurnEndReason.orElse(null),
                lastAssistantMessage.orElse(null),
                events.size()
        );
        projectionCache.put(sessionId, lastSeq, RestoredSession.class, restored);
        return restored;
    }

    // ── Projection helpers ───────────────────────────────────────

    /**
     * 处理project Messages。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private List<Message> projectMessages(List<SessionEvent> events) {
        List<Message> messages = new ArrayList<>();
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.UserMessage um) {
                messages.add(um.data().message());
            } else if (event instanceof SessionEvent.AssistantMessage am) {
                messages.add(am.data().message());
            } else if (event instanceof SessionEvent.ToolResult tr) {
                messages.add(tr.data().message());
            }
        }
        return List.copyOf(messages);
    }

    /**
     * 处理derive Phase。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private Phase derivePhase(List<SessionEvent> events) {
        boolean inTurn = false;
        boolean inStep = false;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.TurnStart) {
                inTurn = true;
                inStep = false;
            } else if (event instanceof SessionEvent.TurnEnd) {
                inTurn = false;
                inStep = false;
            } else if (event instanceof SessionEvent.StepStart) {
                inStep = true;
            } else if (event instanceof SessionEvent.StepEnd) {
                inStep = false;
            }
        }
        if (inStep) return new Phase.Running(0);
        if (inTurn) return new Phase.Maintenance(0);
        return new Phase.Idle(0);
    }

    /**
     * 提取 Latest 待办。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private Optional<TodoWritePayload> extractLatestTodo(List<SessionEvent> events) {
        TodoWritePayload latest = null;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.TodoWrite tw) {
                latest = tw.data();
            }
        }
        return Optional.ofNullable(latest);
    }

    /**
     * 提取 Latest 请求 头信息。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private Optional<RequestHeaderPayload> extractLatestRequestHeader(List<SessionEvent> events) {
        RequestHeaderPayload latest = null;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.RequestHeader rh) {
                latest = rh.data();
            }
        }
        return Optional.ofNullable(latest);
    }

    /**
     * 提取 Latest 请求 上下文。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private Optional<RequestContextPayload> extractLatestRequestContext(List<SessionEvent> events) {
        RequestContextPayload latest = null;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.RequestContext rc) {
                latest = rc.data();
            }
        }
        return Optional.ofNullable(latest);
    }

    /**
     * 处理数量 Turns。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private int countTurns(List<SessionEvent> events) {
        int count = 0;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.TurnStart) {
                count++;
            }
        }
        return count;
    }

    /**
     * 处理数量 Steps。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private int countSteps(List<SessionEvent> events) {
        int count = 0;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.StepStart) {
                count++;
            }
        }
        return count;
    }

    /**
     * 提取 Last Turn End Reason。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private Optional<TurnEndReason> extractLastTurnEndReason(List<SessionEvent> events) {
        TurnEndReason reason = null;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.TurnEnd te) {
                reason = te.reason();
            }
        }
        return Optional.ofNullable(reason);
    }

    /**
     * 提取 Last Assistant 消息。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param events events参数。
     * @return 方法执行结果。
     */
    private Optional<Message> extractLastAssistantMessage(List<SessionEvent> events) {
        Message last = null;
        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.AssistantMessage am) {
                last = am.data().message();
            }
        }
        return Optional.ofNullable(last);
    }

    // ── Result record ────────────────────────────────────────────

    /**
     * RestoredSession 表示RestoredSession会话。
     */
    public record RestoredSession(
            SessionHeader header,
            List<Message> messages,
            Phase phase,
            TodoWritePayload todos,
            RequestHeaderPayload requestHeader,
            RequestContextPayload requestContext,
            int turnCount,
            int stepCount,
            TurnEndReason lastTurnEndReason,
            Message lastAssistantMessage,
            int totalEvents
    ) {}
}
