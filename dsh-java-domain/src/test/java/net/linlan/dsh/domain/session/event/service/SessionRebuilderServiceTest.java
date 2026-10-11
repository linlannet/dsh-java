package net.linlan.dsh.domain.session.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionRebuilderServiceTest {

    @Test
    void shouldReuseProjectionUntilLastSeqChanges() {
        var header = SessionHeader.create("cache-session", Instant.EPOCH, null, null);
        var events = new ArrayList<>(List.<SessionEvent>of(SessionEventFactory.turnStart()));
        var eventLog = new StubEventLogService(header, events);
        var service = new SessionRebuilderService(eventLog, new InMemorySessionProjectionCache());

        var first = service.rebuild("cache-session");
        var second = service.rebuild("cache-session");

        assertEquals(1, eventLog.restoreCount);
        assertEquals(first, second);

        events.add(new SessionEvent.TurnEnd(
                1,
                Instant.now(),
                new net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason.Completed()
        ));
        service.rebuild("cache-session");

        assertEquals(2, eventLog.restoreCount);
    }

    private static final class StubEventLogService implements ISessionEventLogService {
        private final SessionHeader header;
        private final List<SessionEvent> events;
        private int restoreCount;

        private StubEventLogService(SessionHeader header, List<SessionEvent> events) {
            this.header = header;
            this.events = events;
        }

        @Override public SessionHeader createSession(SessionHeader header) { return header; }
        @Override public SessionEvent append(String sessionId, SessionEvent event) { return event; }
        @Override public List<SessionEvent> appendBatch(String sessionId, List<SessionEvent> events) { return events; }
        @Override public List<SessionEvent> readAll(String sessionId) { return List.copyOf(events); }
        @Override public List<SessionEvent> readAfter(String sessionId, long afterSeq) { return List.copyOf(events); }
        @Override public SessionHeader getHeader(String sessionId) { return header; }
        @Override public long getLastSeq(String sessionId) { return events.isEmpty() ? -1 : events.get(events.size() - 1).seq(); }
        @Override public void restore(String sessionId) { restoreCount++; }
    }
}
