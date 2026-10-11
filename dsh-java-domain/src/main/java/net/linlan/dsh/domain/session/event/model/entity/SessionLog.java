package net.linlan.dsh.domain.session.event.model.entity;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.session.event.model.valobj.RequestHeaderPayload;
import net.linlan.dsh.domain.session.event.model.valobj.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单个会话的内存有序事件日志。
 * <p>
 * 这里承担 WAL 缓冲职责：事件按单调、无空洞的序列号顺序追加，
 * 后续可以重放事件来重建状态。
 * <p>
 * {@code SessionLog} 负责分配序列号。调用方提供不含 seq 的事件数据，
 * {@link #append(SessionEvent)} 会分配下一个顺序序列号。
 * <p>
 * 线程安全：所有变更方法都使用同步控制。
 *
 *
 */
public class SessionLog {

    private final String sessionId;
    private final SessionHeader header;
    private final List<SessionEvent> events = new ArrayList<>();
    private long nextSeq = 0;

    /**
     * 处理会话 Log。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param header 头信息参数。
     * @return 方法执行结果。
     */
    public SessionLog(String sessionId, SessionHeader header) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.header = Objects.requireNonNull(header, "header");
    }

    /**
     * 处理会话 ID。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * 处理头信息。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public SessionHeader header() {
        return header;
    }

    /**
     * 当前事件数量。
     */
    public synchronized int size() {
        return events.size();
    }

    /**
     * 下一个待分配的序列号，等于当前事件数量。
     */
    public synchronized long nextSeq() {
        return nextSeq;
    }

    /**
     * 追加一个事件，并分配下一个顺序序列号。
     * <p>
     * 如果事件自带 seq 且正好等于 nextSeq，则保持不变；
     * 否则会使用分配后的 seq 创建新事件。
     *
     * @param event 待追加事件，seq 会被分配或覆盖
     * @return 携带正确 seq 的已追加事件
     */
    public synchronized SessionEvent append(SessionEvent event) {
        return appendInternal(event);
    }

    /**
     * 批量追加事件，并按顺序分配序列号。
     * <p>注意：内部走 {@link #appendInternal} 而非 {@link #append}，避免子类覆写 append
     * 后批量追加触发重复副作用（如持久化镜像被写入两次）。</p>
     */
    public synchronized List<SessionEvent> appendBatch(List<SessionEvent> batch) {
        List<SessionEvent> result = new ArrayList<>(batch.size());
        for (SessionEvent e : batch) {
            result.add(appendInternal(e));
        }
        return result;
    }

    private SessionEvent appendInternal(SessionEvent event) {
        SessionEvent withSeq = reassignSeq(event, nextSeq);
        events.add(withSeq);
        nextSeq++;
        return withSeq;
    }

    /**
     * 处理events。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public synchronized List<SessionEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * 处理events After。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param afterSeq after Seq参数。
     * @return 方法执行结果。
     */
    public synchronized List<SessionEvent> eventsAfter(long afterSeq) {
        List<SessionEvent> result = new ArrayList<>();
        for (SessionEvent e : events) {
            if (e.seq() > afterSeq) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * 处理events Range。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fromSeq from Seq参数。
     * @param toSeq to Seq参数。
     * @return 方法执行结果。
     */
    public synchronized List<SessionEvent> eventsRange(long fromSeq, long toSeq) {
        List<SessionEvent> result = new ArrayList<>();
        for (SessionEvent e : events) {
            if (e.seq() >= fromSeq && e.seq() <= toSeq) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * 处理last 事件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public synchronized SessionEvent lastEvent() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    /**
     * 处理last Seq。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public synchronized long lastSeq() {
        return events.isEmpty() ? -1 : events.get(events.size() - 1).seq();
    }

    /**
     * 查找最后一条 {@code session/end-seed} 事件的序列号。
     * 该事件是种子历史和实时工作的边界。
     *
     * @return 最后一条 end-seed 事件的序列号；不存在时返回 -1
     */
    public synchronized long lastEndSeedSeq() {
        long result = -1;
        for (SessionEvent e : events) {
            if (e.type() == SessionEventType.SESSION_END_SEED) {
                result = e.seq();
            }
        }
        return result;
    }

    /**
     * 处理firstLiveSeq。
     * @return 领域处理结果。
     */
    public synchronized long firstLiveSeq() {
        long endSeed = lastEndSeedSeq();
        return endSeed < 0 ? 0 : endSeed + 1;
    }

    /**
     * 处理restore。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param restored restored。
     * @return 领域处理结果。
     */
    public synchronized void restore(List<SessionEvent> restored) {
        events.clear();
        events.addAll(restored);
        nextSeq = restored.isEmpty() ? 0 : restored.get(restored.size() - 1).seq() + 1;
    }

    // ── Surface projection (deriveMessages, requestHeader, requestContext) ──

    /**
     * 常量字段。
     * <p>用途：保存该字段对应的领域状态或配置。</p>
     */
    private List<Message> derivedCache = null;
    /**
     * 常量字段。
     * <p>用途：保存该字段对应的领域状态或配置。</p>
     */
    private long derivedCacheSeq = -1;

    /**
     * 处理deriveMessages消息列表。
     * @return 领域处理结果。
     */
    public synchronized List<Message> deriveMessages() {
        // Simple projection: rebuild from scratch each time for correctness.
        // The TS version incrementally caches by surface node, but the Java
        // version favors simplicity until profiling shows a hot path.
        List<Message> messages = new ArrayList<>();
        // Track which seqs are "replaced" (shadowed by a replace operation)
        java.util.Set<Long> shadowed = new java.util.HashSet<>();
        // Track append-order seqs that are currently on the surface
        List<Long> surfaceSeqs = new ArrayList<>();

        for (SessionEvent event : events) {
            if (!event.isSurfaceEvent()) continue;
            SurfaceOp op = event.surfaceIntent() != null
                    ? event.surfaceIntent().surfaceOp()
                    : null;
            if (op == null) continue;

            if (op instanceof SurfaceOp.Append) {
                Message msg = projectMessage(event);
                if (msg != null) {
                    surfaceSeqs.add(event.seq());
                    messages.add(msg);
                }
            } else if (op instanceof SurfaceOp.Replace replace) {
                // Shadow the replaced range [start, end]
                long startSeq = replace.start();
                long endSeq = replace.end();
                // Remove shadowed entries from surface
                java.util.Iterator<Long> it = surfaceSeqs.iterator();
                java.util.Iterator<Message> mit = messages.iterator();
                while (it.hasNext()) {
                    long seq = it.next();
                    if (seq >= startSeq && seq <= endSeq) {
                        it.remove();
                        mit.next();
                        mit.remove();
                    } else if (seq > endSeq) {
                        break;
                    } else {
                        mit.next();
                    }
                }
                // Add the replacing event
                Message msg = projectMessage(event);
                if (msg != null) {
                    surfaceSeqs.add(event.seq());
                    messages.add(msg);
                }
            }
        }
        return Collections.unmodifiableList(messages);
    }

    /**
     * 处理projectMessage消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param event 事件。
     * @return 领域处理结果。
     */
    private Message projectMessage(SessionEvent event) {
        if (event instanceof SessionEvent.UserMessage um) {
            UserMessagePayload payload = um.data();
            return payload.message();
        } else if (event instanceof SessionEvent.AssistantMessage am) {
            AssistantMessagePayload payload = am.data();
            return payload.message();
        } else if (event instanceof SessionEvent.ToolResult tr) {
            ToolResultPayload payload = tr.data();
            return payload.message();
        }
        return null;
    }

    /**
     * 常量字段。
     * <p>用途：保存该字段对应的领域状态或配置。</p>
     */
    private RequestHeaderPayload headerFold = null;
    private long headerFoldSeq = -1;

    /**
     * 处理请求requestHeader。
     * @return 领域处理结果。
     */
    public synchronized RequestHeaderPayload requestHeader() {
        if (headerFoldSeq < events.size()) {
            for (SessionEvent e : events.subList(
                    headerFoldSeq < 0 ? 0 : (int) headerFoldSeq, events.size())) {
                if (e instanceof SessionEvent.RequestHeader rh) {
                    headerFold = rh.data();
                }
            }
            headerFoldSeq = events.size();
        }
        return headerFold;
    }

    /**
     * 常量字段。
     * <p>用途：保存该字段对应的领域状态或配置。</p>
     */
    private RequestContextPayload contextFold = null;
    private long contextFoldSeq = -1;

    /**
     * 处理请求requestContext。
     * @return 领域处理结果。
     */
    public synchronized RequestContextPayload requestContext() {
        if (contextFoldSeq < events.size()) {
            for (SessionEvent e : events.subList(
                    contextFoldSeq < 0 ? 0 : (int) contextFoldSeq, events.size())) {
                if (e instanceof SessionEvent.RequestContext rc) {
                    contextFold = rc.data();
                }
            }
            contextFoldSeq = events.size();
        }
        return contextFold;
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * 处理reassign Seq。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param event 领域事件。
     * @param seq 事件序号。
     * @return 方法执行结果。
     */
    private SessionEvent reassignSeq(SessionEvent event, long seq) {
        if (event instanceof SessionEvent.TurnStart e) {
            return new SessionEvent.TurnStart(seq, e.time());
        } else if (event instanceof SessionEvent.TurnEnd e) {
            return new SessionEvent.TurnEnd(seq, e.time(), e.reason());
        } else if (event instanceof SessionEvent.StepStart e) {
            return new SessionEvent.StepStart(seq, e.time(), e.turn(), e.step());
        } else if (event instanceof SessionEvent.StepEnd e) {
            return new SessionEvent.StepEnd(seq, e.time(), e.turn(), e.step());
        } else if (event instanceof SessionEvent.UserMessage e) {
            return new SessionEvent.UserMessage(seq, e.time(), e.data(), e.surfaceIntent());
        } else if (event instanceof SessionEvent.AssistantChunk e) {
            return new SessionEvent.AssistantChunk(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.AssistantMessage e) {
            return new SessionEvent.AssistantMessage(seq, e.time(), e.data(), e.surfaceIntent());
        } else if (event instanceof SessionEvent.ToolCall e) {
            return new SessionEvent.ToolCall(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.ToolResult e) {
            return new SessionEvent.ToolResult(seq, e.time(), e.data(), e.surfaceIntent());
        } else if (event instanceof SessionEvent.TodoWrite e) {
            return new SessionEvent.TodoWrite(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.RequestHeader e) {
            return new SessionEvent.RequestHeader(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.RequestContext e) {
            return new SessionEvent.RequestContext(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.SessionEndSeed e) {
            return new SessionEvent.SessionEndSeed(seq, e.time());
        } else if (event instanceof SessionEvent.AgentInboxSpliced e) {
            return new SessionEvent.AgentInboxSpliced(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.PlanModeChange e) {
            return new SessionEvent.PlanModeChange(seq, e.time(), e.data());
        } else if (event instanceof SessionEvent.Generic e) {
            return new SessionEvent.Generic(seq, e.time(), e.type(), e.payload());
        } else {
            throw new IllegalStateException("Unknown SessionEvent variant: " + event.getClass());
        }
    }
}
