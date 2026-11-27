package net.linlan.dsh.cases.session;

import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionForkServiceTest {

    @Test
    void missingParentPrefixFailsBeforePersistence() {
        List<String> operations = new ArrayList<>();
        SessionForkService service = new SessionForkService(eventLog(operations, List.of()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.fork("parent", null, "fork", "/tmp", "general"));

        assertEquals("SESSION_PARENT_PREFIX_REQUIRED", error.getMessage());
        assertTrue(operations.isEmpty());
    }

    @Test
    void prefixIsPersistedBeforeForkIsReturned() {
        List<String> operations = new ArrayList<>();
        SessionEvent prefixEvent = new SessionEvent.Generic(
                0, Instant.now(), SessionEventType.SESSION_END_SEED, "");
        SessionForkService service = new SessionForkService(eventLog(operations, List.of(prefixEvent)));

        var fork = service.fork("parent", List.of(prefixEvent), "fork", "/tmp", "general");

        assertEquals(List.of("createSession", "appendBatch", "readAll"), operations);
        assertEquals("parent", fork.header().parentSession());
        assertEquals(1, fork.header().seedLength());
        assertEquals(1, fork.events().size());
    }

    private static ISessionEventLogService eventLog(List<String> operations, List<SessionEvent> events) {
        return (ISessionEventLogService) Proxy.newProxyInstance(
                SessionForkServiceTest.class.getClassLoader(),
                new Class<?>[]{ISessionEventLogService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "createSession", "appendBatch" -> operations.add(method.getName());
                        case "readAll" -> {
                            operations.add("readAll");
                            return events;
                        }
                        case "getLastSeq" -> {
                            return (long) events.size();
                        }
                        default -> {
                            return null;
                        }
                    }
                    return null;
                });
    }
}
