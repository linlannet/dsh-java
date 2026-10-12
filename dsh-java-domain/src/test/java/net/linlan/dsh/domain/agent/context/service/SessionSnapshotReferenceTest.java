package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionSnapshotReference 只读快照")
class SessionSnapshotReferenceTest {

    @Test
    @DisplayName("有效 sessionId 返回只读快照")
    void validSessionIdReturnsSnapshot() {
        var ref = new SessionSnapshotReference(sid -> createSessionLog(sid));
        var snapshot = ref.loadReadOnly("sess-1");
        assertTrue(snapshot.isPresent());
        assertEquals("sess-1", snapshot.get().sessionId());
        assertEquals(0, snapshot.get().events().size());
    }

    @Test
    @DisplayName("null sessionId 返回 empty")
    void nullSessionIdReturnsEmpty() {
        var ref = new SessionSnapshotReference(sid -> null);
        assertTrue(ref.loadReadOnly(null).isEmpty());
    }

    @Test
    @DisplayName("空 sessionId 返回 empty")
    void blankSessionIdReturnsEmpty() {
        var ref = new SessionSnapshotReference(sid -> null);
        assertTrue(ref.loadReadOnly("").isEmpty());
        assertTrue(ref.loadReadOnly("  ").isEmpty());
    }

    @Test
    @DisplayName("sessionLoader 返回 null 时返回 empty")
    void nullSessionLogReturnsEmpty() {
        var ref = new SessionSnapshotReference(sid -> null);
        assertTrue(ref.loadReadOnly("sess-1").isEmpty());
    }

    @Test
    @DisplayName("sessionLoader 抛异常时返回 empty")
    void exceptionReturnsEmpty() {
        var ref = new SessionSnapshotReference(sid -> { throw new RuntimeException("not found"); });
        assertTrue(ref.loadReadOnly("sess-1").isEmpty());
    }

    @Test
    @DisplayName("null sessionLoader 抛异常")
    void nullLoaderRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SessionSnapshotReference(null));
    }

    private SessionLog createSessionLog(String sessionId) {
        var header = SessionHeader.create(sessionId, Instant.now(), "/tmp", "default");
        return new SessionLog(sessionId, header);
    }
}
