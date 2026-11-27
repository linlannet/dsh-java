package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

import java.util.List;

/**
 * 冷会话的只读句柄；不暴露 append、restore 等写操作。
 */
public final class SessionHandle implements AutoCloseable {

    private final SessionLog session;
    private final CompactionSummaryProjection summaryProjection;
    private boolean closed;

    SessionHandle(SessionLog session, CompactionSummaryProjection summaryProjection) {
        this.session = session;
        this.summaryProjection = summaryProjection;
    }

    public String sessionId() {
        return session.sessionId();
    }

    public SessionHeader header() {
        ensureOpen();
        return session.header();
    }

    public List<SessionEvent> events() {
        ensureOpen();
        return session.events();
    }

    public List<Message> messages() {
        ensureOpen();
        return session.deriveMessages();
    }

    public java.util.Optional<CompactionSummaryProjection.CompactionSummary> compactionSummary() {
        ensureOpen();
        return summaryProjection.project(session);
    }

    @Override
    public void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SESSION_QUERY_HANDLE_CLOSED");
        }
    }
}
