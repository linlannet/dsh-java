package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * SessionSnapshotReference 解析跨 session 引用并加载目标 Session 只读快照。
 * <p>消息含 session 引用时注入上下文，不可修改目标 Session。
 */
public final class SessionSnapshotReference {

    private final Function<String, SessionLog> sessionLoader;

    public SessionSnapshotReference(Function<String, SessionLog> sessionLoader) {
        if (sessionLoader == null) {
            throw new IllegalArgumentException("sessionLoader must not be null");
        }
        this.sessionLoader = sessionLoader;
    }

    /**
     * 加载目标 Session 的只读快照。
     *
     * @param sessionId 目标会话 ID
     * @return 只读快照（不存在时返回 Optional.empty()）
     */
    public Optional<ReadOnlySnapshot> loadReadOnly(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            SessionLog log = sessionLoader.apply(sessionId);
            if (log == null) {
                return Optional.empty();
            }
            return Optional.of(new ReadOnlySnapshot(
                    log.sessionId(),
                    log.header(),
                    List.copyOf(log.events())
            ));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * ReadOnlySnapshot 是目标 Session 的只读快照。
     * <p>不可修改，仅用于上下文注入。
     *
     * @param sessionId 会话 ID
     * @param header    会话头
     * @param events    事件列表（不可变副本）
     */
    public record ReadOnlySnapshot(
            String sessionId,
            SessionHeader header,
            List<net.linlan.dsh.domain.session.event.model.entity.SessionEvent> events
    ) {
        public ReadOnlySnapshot {
            events = List.copyOf(events);
        }
    }
}
