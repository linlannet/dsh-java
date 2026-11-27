package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceIntent;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionQueryServiceTest {

    @Test
    void projectsLatestCompactionSummaryFromEvents() {
        Message summaryMessage = Message.createUser(
                List.of(new TextBlock("[Compacted History]\n\n保留的决定")),
                new MessageSource.UserSource());
        SessionEvent event = SessionEventFactory.userMessage(
                0, 0, summaryMessage,
                new SurfaceIntent(new SurfaceOp.Replace(2, 7), null));
        SessionHeader header = SessionHeader.create("cold-1", Instant.now(), "/tmp", "general");
        ISessionEventLogService eventLog = eventLog(header, List.of(event));

        try (SessionHandle handle = new SessionQueryService(eventLog).open("cold-1")) {
            var summary = handle.compactionSummary().orElseThrow();
            assertEquals("保留的决定", summary.summary());
            assertEquals(2, summary.startSeq());
            assertEquals(7, summary.endSeq());
            assertEquals(1, handle.events().size());
            assertThrows(UnsupportedOperationException.class,
                    () -> handle.events().add(event));
        }
    }

    @Test
    void closedHandleRejectsFurtherReads() {
        SessionHeader header = SessionHeader.create("cold-2", Instant.now(), "/tmp", "general");
        SessionHandle handle = new SessionQueryService(eventLog(header, List.of())).open("cold-2");
        handle.close();
        IllegalStateException error = assertThrows(IllegalStateException.class, handle::events);
        assertEquals("SESSION_QUERY_HANDLE_CLOSED", error.getMessage());
    }

    @Test
    void blankSessionIdIsRejected() {
        SessionHeader header = SessionHeader.create("cold-3", Instant.now(), "/tmp", "general");
        assertThrows(IllegalArgumentException.class,
                () -> new SessionQueryService(eventLog(header, List.of())).open(" "));
    }

    private static ISessionEventLogService eventLog(SessionHeader header, List<SessionEvent> events) {
        return (ISessionEventLogService) Proxy.newProxyInstance(
                SessionQueryServiceTest.class.getClassLoader(),
                new Class<?>[]{ISessionEventLogService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> header;
                    case "readAll" -> events;
                    case "getLastSeq" -> (long) events.size();
                    case "readAfter" -> List.of();
                    default -> null;
                });
    }
}
