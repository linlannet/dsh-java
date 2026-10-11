package net.linlan.dsh.domain.session.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionEventLogServiceTest {

    private final SessionWriteLeaseService leaseService = new SessionWriteLeaseService();

    @Test
    void shouldAppendAndReleaseLease() {
        var header = SessionHeader.create("lease-session", Instant.EPOCH, null, null);
        var store = new StubSessionEventStore(header);
        var service = new SessionEventLogService(store, leaseService);

        service.append("lease-session", SessionEventFactory.turnStart());
        service.append("lease-session", SessionEventFactory.turnEnd(new net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason.Completed()));

        assertEquals(2, store.events.size());
    }

    @Test
    void shouldRejectAppendWhenLeaseIsHeld() {
        var header = SessionHeader.create("locked-session", Instant.EPOCH, null, null);
        var store = new StubSessionEventStore(header);
        var service = new SessionEventLogService(store, leaseService);
        leaseService.acquire("locked-session", "external-writer", Duration.ofSeconds(5));

        assertThrows(SessionWriteLeaseConflictException.class,
                () -> service.append("locked-session", SessionEventFactory.turnStart()));
    }

    private static final class StubSessionEventStore implements SessionPersistenceSeam {
        private final SessionHeader header;
        private final List<SessionEvent> events = new ArrayList<>();

        private StubSessionEventStore(SessionHeader header) {
            this.header = header;
        }

        @Override public void saveHeader(SessionHeader header) {}
        @Override public Optional<SessionHeader> getHeader(String sessionId) { return Optional.of(header); }
        @Override public void append(String sessionId, SessionEvent event) { events.add(event); }
        @Override public void appendBatch(String sessionId, List<SessionEvent> events) { this.events.addAll(events); }
        @Override public List<SessionEvent> readAll(String sessionId) { return List.copyOf(events); }
        @Override public List<SessionEvent> readAfter(String sessionId, long afterSeq) { return List.copyOf(events); }
        @Override public List<SessionEvent> readRange(String sessionId, long fromSeq, long toSeq) { return List.copyOf(events); }
        @Override public long getLastSeq(String sessionId) { return events.isEmpty() ? -1 : events.get(events.size() - 1).seq(); }
        @Override public long count(String sessionId) { return events.size(); }
        @Override public List<SessionHeader> listHeaders(int limit) { return List.of(header); }
    }
}
