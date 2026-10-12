package net.linlan.dsh.domain.sdk.service;

import net.linlan.dsh.domain.sdk.ISdkServerService;
import net.linlan.dsh.domain.sdk.adapter.port.ISdkTransportPort;
import net.linlan.dsh.domain.sdk.model.valobj.McpToolInfo;
import net.linlan.dsh.domain.sdk.model.valobj.ResourceInfo;
import net.linlan.dsh.domain.sdk.model.valobj.SessionDetail;
import net.linlan.dsh.domain.sdk.model.valobj.SessionSummary;
import net.linlan.dsh.domain.sdk.model.valobj.ToolInfo;
import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdkServerServiceTest {

    @Test
    void emitNotificationDelegatesToTransportNotify() {
        RecordingTransport transport = new RecordingTransport();
        SdkServerService service = new SdkServerService(transport);

        service.emitNotification("session/updated", Map.of("id", "s1"));

        assertEquals(1, transport.notifications.size());
        assertEquals("session/updated", transport.notifications.get(0).method());
        assertEquals(Map.of("id", "s1"), transport.notifications.get(0).params());
    }

    @Test
    void cancelSessionEmitsCancelledNotificationAndReturnsTrue() {
        RecordingTransport transport = new RecordingTransport();
        SdkServerService service = new SdkServerService(transport);

        boolean result = service.cancelSession("s1");

        assertTrue(result);
        assertEquals(1, transport.notifications.size());
        assertEquals("session/cancelled", transport.notifications.get(0).method());
        assertEquals(Map.of("sessionId", "s1"), transport.notifications.get(0).params());
    }

    @Test
    void cancelToolEmitsCancelledNotificationAndReturnsTrue() {
        RecordingTransport transport = new RecordingTransport();
        SdkServerService service = new SdkServerService(transport);

        boolean result = service.cancelTool("s1", "call-1");

        assertTrue(result);
        assertEquals(1, transport.notifications.size());
        assertEquals("tool/cancelled", transport.notifications.get(0).method());
        assertEquals(Map.of("sessionId", "s1", "callId", "call-1"), transport.notifications.get(0).params());
    }

    @Test
    void stopClosesTransport() {
        RecordingTransport transport = new RecordingTransport();
        SdkServerService service = new SdkServerService(transport);

        service.stop();

        assertTrue(transport.closed);
    }

    @Test
    void startIsNoopAndDoesNotInteractWithTransport() {
        RecordingTransport transport = new RecordingTransport();
        SdkServerService service = new SdkServerService(transport);

        service.start();

        assertTrue(transport.notifications.isEmpty());
        assertTrue(!transport.closed);
    }

    @Test
    void listSessionsReturnsEmptyListWhenNoEventLogService() {
        SdkServerService service = new SdkServerService(new RecordingTransport());

        List<SessionSummary> sessions = service.listSessions(10);

        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void listSessionsReturnsEmptyListEvenWithEventLogService() {
        SdkServerService service = new SdkServerService(new RecordingTransport(), new StubEventLogService());

        assertTrue(service.listSessions(10).isEmpty());
    }

    @Test
    void loadSessionReturnsNullWhenNoEventLogService() {
        SdkServerService service = new SdkServerService(new RecordingTransport());

        assertNull(service.loadSession("s1"));
    }

    @Test
    void loadSessionReturnsDetailAggregatedFromEventLog() {
        StubEventLogService eventLog = new StubEventLogService();
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        eventLog.header = SessionHeader.create("s1", createdAt, "/work", "default");
        eventLog.events = List.of();
        SdkServerService service = new SdkServerService(new RecordingTransport(), eventLog);

        SessionDetail detail = service.loadSession("s1");

        assertNotNull(detail);
        assertEquals("s1", detail.sessionId());
        assertEquals("active", detail.status());
        assertEquals(createdAt, detail.createdAt());
        assertTrue(detail.events().isEmpty());
    }

    @Test
    void loadSessionReturnsNullWhenEventLogThrows() {
        StubEventLogService eventLog = new StubEventLogService();
        eventLog.throwOnRead = true;
        SdkServerService service = new SdkServerService(new RecordingTransport(), eventLog);

        assertNull(service.loadSession("s1"));
    }

    @Test
    void streamSessionIsNoopWhenNoEventLogService() {
        SdkServerService service = new SdkServerService(new RecordingTransport());
        boolean[] invoked = {false};

        service.streamSession("s1", event -> invoked[0] = true);

        assertTrue(!invoked[0]);
    }

    @Test
    void streamSessionForwardsEachEventToSink() {
        StubEventLogService eventLog = new StubEventLogService();
        eventLog.events = List.of();
        SdkServerService service = new SdkServerService(new RecordingTransport(), eventLog);
        List<Object> sink = new ArrayList<>();

        service.streamSession("s1", sink::add);

        assertTrue(sink.isEmpty());
    }

    @Test
    void streamSessionSwallowsExceptionsFromEventLog() {
        StubEventLogService eventLog = new StubEventLogService();
        eventLog.throwOnRead = true;
        SdkServerService service = new SdkServerService(new RecordingTransport(), eventLog);

        service.streamSession("s1", event -> { });
    }

    @Test
    void listToolsReturnsEmptyList() {
        SdkServerService service = new SdkServerService(new RecordingTransport());

        List<ToolInfo> tools = service.listTools("s1");

        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void listMcpToolsReturnsEmptyList() {
        SdkServerService service = new SdkServerService(new RecordingTransport());

        List<McpToolInfo> tools = service.listMcpTools();

        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void listResourcesReturnsEmptyList() {
        SdkServerService service = new SdkServerService(new RecordingTransport());

        List<ResourceInfo> resources = service.listResources("scope");

        assertNotNull(resources);
        assertTrue(resources.isEmpty());
    }

    @Test
    void serviceImplementsISdkServerService() {
        SdkServerService service = new SdkServerService(new RecordingTransport());

        assertTrue(service instanceof ISdkServerService);
    }

    private static final class RecordingTransport implements ISdkTransportPort {
        final List<NotificationFrame> notifications = new ArrayList<>();
        boolean closed = false;

        @Override
        public Object request(String method, Object params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void notify(String method, Object params) {
            notifications.add(new NotificationFrame(method, params));
        }

        @Override
        public void onNotification(Consumer<NotificationFrame> handler) { }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class StubEventLogService implements ISessionEventLogService {
        SessionHeader header;
        List<SessionEvent> events = List.of();
        boolean throwOnRead = false;

        @Override
        public SessionHeader createSession(SessionHeader header) { return header; }

        @Override
        public SessionEvent append(String sessionId, SessionEvent event) { return event; }

        @Override
        public List<SessionEvent> appendBatch(String sessionId, List<SessionEvent> events) { return events; }

        @Override
        public List<SessionEvent> readAll(String sessionId) {
            if (throwOnRead) {
                throw new RuntimeException("boom");
            }
            return events;
        }

        @Override
        public List<SessionEvent> readAfter(String sessionId, long afterSeq) { return events; }

        @Override
        public SessionHeader getHeader(String sessionId) { return header; }

        @Override
        public long getLastSeq(String sessionId) { return 0L; }

        @Override
        public void restore(String sessionId) { }
    }
}
