package net.linlan.dsh.domain.session.event.service;

import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.projection.service.ProjectionCache;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * SessionEventLogService 封装会话事件SessionEventLog的领域规则与流程。
 */
@Service
public class SessionEventLogService implements ISessionEventLogService {

    private final SessionPersistenceSeam store;
    private final SessionWriteLeaseService writeLeaseService;
    private final Map<String, SessionLog> logs = new ConcurrentHashMap<>();
    private volatile ProjectionCache projectionCache;

    /**
     * 处理会话 事件 Log Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param store store参数。
     * @return 方法执行结果。
     */
    public SessionEventLogService(
            SessionPersistenceSeam store,
            SessionWriteLeaseService writeLeaseService
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.writeLeaseService = Objects.requireNonNull(writeLeaseService, "writeLeaseService");
    }

    public void setProjectionCache(ProjectionCache projectionCache) {
        this.projectionCache = projectionCache;
    }

    @Override
    public SessionHeader createSession(SessionHeader header) {
        return withWriteLease(header.sessionId(), () -> {
            store.saveHeader(header);
            SessionLog log = new SessionLog(header.sessionId(), header);
            logs.put(header.sessionId(), log);
            return header;
        });
    }
    /**
     * 追加目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param event 领域事件。
     * @return 方法执行结果。
     */


    @Override
    public SessionEvent append(String sessionId, SessionEvent event) {
        return withWriteLease(sessionId, () -> {
            SessionLog log = getOrCreateLog(sessionId);
            SessionEvent appended = log.append(event);
            store.append(sessionId, appended);
            triggerProjection(appended);
            return appended;
        });
    }
    /**
     * 追加Batch。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param events events参数。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> appendBatch(String sessionId, List<SessionEvent> events) {
        return withWriteLease(sessionId, () -> {
            SessionLog log = getOrCreateLog(sessionId);
            List<SessionEvent> appended = log.appendBatch(events);
            store.appendBatch(sessionId, appended);
            for (SessionEvent e : appended) {
                triggerProjection(e);
            }
            return appended;
        });
    }
    /**
     * 读取All。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> readAll(String sessionId) {
        SessionLog log = logs.get(sessionId);
        if (log != null) {
            return log.events();
        }
        return store.readAll(sessionId);
    }
    /**
     * 读取After。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param afterSeq after Seq参数。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEvent> readAfter(String sessionId, long afterSeq) {
        SessionLog log = logs.get(sessionId);
        if (log != null) {
            return log.eventsAfter(afterSeq);
        }
        return store.readAfter(sessionId, afterSeq);
    }
    /**
     * 获取Header。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 获取到的Header。
     */


    @Override
    public SessionHeader getHeader(String sessionId) {
        return store.getHeader(sessionId)
                .orElseThrow(() -> new IllegalStateException("No session header for: " + sessionId));
    }
    /**
     * 获取Last Seq。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 获取到的Last Seq。
     */


    @Override
    public long getLastSeq(String sessionId) {
        SessionLog log = logs.get(sessionId);
        if (log != null) {
            return log.lastSeq();
        }
        return store.getLastSeq(sessionId);
    }
    /**
     * restore。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     */


    @Override
    public void restore(String sessionId) {
        SessionHeader header = getHeader(sessionId);
        List<SessionEvent> events = store.readAll(sessionId);
        SessionLog log = new SessionLog(sessionId, header);
        log.restore(events);
        logs.put(sessionId, log);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void triggerProjection(SessionEvent event) {
        ProjectionCache pc = this.projectionCache;
        if (pc != null) {
            pc.onEventAppended(event);
        }
    }

    private <T> T withWriteLease(String sessionId, java.util.function.Supplier<T> action) {
        var lease = writeLeaseService.acquire(sessionId, "session-event-log:" + UUID.randomUUID(), Duration.ofSeconds(30));
        try {
            return action.get();
        } finally {
            writeLeaseService.release(sessionId, lease.leaseId());
        }
    }

    /**
     * 获取OrCreateLog。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    private SessionLog getOrCreateLog(String sessionId) {
        return logs.computeIfAbsent(sessionId, sid -> {
            SessionHeader header = store.getHeader(sid)
                    .orElseThrow(() -> new IllegalStateException("No session header for: " + sid));
            List<SessionEvent> existing = store.readAll(sid);
            SessionLog log = new SessionLog(sid, header);
            if (!existing.isEmpty()) {
                log.restore(existing);
            }
            return log;
        });
    }
}
