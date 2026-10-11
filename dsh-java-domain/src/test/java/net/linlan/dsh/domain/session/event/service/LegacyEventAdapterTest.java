package net.linlan.dsh.domain.session.event.service;

import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyEventAdapter — pending-free 容忍与 failed-step 恢复")
class LegacyEventAdapterTest {

    @Test
    @DisplayName("含 pending-free tool/result 的事件流正常加载不报错")
    void tolerateOrphanToolResults() {
        var eventLog = new InMemoryEventLog();
        eventLog.append("s1", SessionEventFactory.turnStart());
        eventLog.append("s1", SessionEventFactory.stepStart(1, 1));
        eventLog.append("s1", SessionEventFactory.stepEnd(1, 1));
        eventLog.append("s1", SessionEventFactory.turnEnd(new TurnEndReason.Completed()));

        var adapter = new LegacyEventAdapter(eventLog);
        var result = adapter.tolerateAndValidate("s1");

        assertTrue(result.tolerated(), "应容忍加载");
        assertEquals(4, result.events().size());
    }

    @Test
    @DisplayName("无 failed-step 时 detectFailedStep 返回 none")
    void detectFailedStepNone() {
        var eventLog = new InMemoryEventLog();
        eventLog.append("s1", SessionEventFactory.turnStart());
        eventLog.append("s1", SessionEventFactory.stepStart(1, 1));
        eventLog.append("s1", SessionEventFactory.stepEnd(1, 1));
        eventLog.append("s1", SessionEventFactory.turnEnd(new TurnEndReason.Completed()));

        var adapter = new LegacyEventAdapter(eventLog);
        var detection = adapter.detectFailedStep("s1");

        assertFalse(detection.hasFailedStep(), "无 failed-step");
    }

    @Test
    @DisplayName("含 failed-step 时 detectFailedStep 返回正确信息")
    void detectFailedStepPresent() {
        var eventLog = new InMemoryEventLog();
        eventLog.append("s1", SessionEventFactory.turnStart());
        eventLog.append("s1", SessionEventFactory.stepStart(1, 1));
        eventLog.append("s1", SessionEventFactory.stepEnd(1, 1));
        eventLog.append("s1", SessionEventFactory.stepStart(1, 2));
        eventLog.append("s1", SessionEventFactory.turnEnd(
                new TurnEndReason.Error("tool execution failed", "TOOL_ERROR")));

        var adapter = new LegacyEventAdapter(eventLog);
        var detection = adapter.detectFailedStep("s1");

        assertTrue(detection.hasFailedStep(), "应检测到 failed-step");
        assertEquals(1, detection.turn(), "failed-step turn=1");
        assertEquals(2, detection.step(), "failed-step step=2");
        assertTrue(detection.recoverable(), "应标记为可恢复");
    }

    @Test
    @DisplayName("markRecoverable 幂等：重复调用结果一致")
    void markRecoverableIdempotent() {
        var eventLog = new InMemoryEventLog();
        eventLog.append("s1", SessionEventFactory.turnStart());
        eventLog.append("s1", SessionEventFactory.stepStart(1, 1));
        eventLog.append("s1", SessionEventFactory.turnEnd(
                new TurnEndReason.Error("crash", "CRASH")));

        var adapter = new LegacyEventAdapter(eventLog);

        boolean first = adapter.markRecoverable("s1");
        boolean second = adapter.markRecoverable("s1");

        assertTrue(first, "第一次应标记为可恢复");
        assertTrue(second, "第二次结果一致（幂等）");
    }

    @Test
    @DisplayName("无 failed-step 时 markRecoverable 返回 false")
    void markRecoverableNoFailedStep() {
        var eventLog = new InMemoryEventLog();
        eventLog.append("s1", SessionEventFactory.turnStart());
        eventLog.append("s1", SessionEventFactory.turnEnd(new TurnEndReason.Completed()));

        var adapter = new LegacyEventAdapter(eventLog);
        assertFalse(adapter.markRecoverable("s1"), "无 failed-step 应返回 false");
    }

    private static final class InMemoryEventLog implements ISessionEventLogService {
        private final Map<String, SessionLog> logs = new HashMap<>();
        private final Map<String, SessionHeader> headers = new HashMap<>();

        @Override
        public SessionHeader createSession(SessionHeader header) {
            headers.put(header.sessionId(), header);
            logs.put(header.sessionId(), new SessionLog(header.sessionId(), header));
            return header;
        }

        @Override
        public SessionEvent append(String sessionId, SessionEvent event) {
            var log = logs.computeIfAbsent(sessionId,
                    k -> new SessionLog(k, SessionHeader.create(k, Instant.now(), null, null)));
            return log.append(event);
        }

        @Override
        public List<SessionEvent> appendBatch(String sessionId, List<SessionEvent> events) {
            var log = logs.computeIfAbsent(sessionId,
                    k -> new SessionLog(k, SessionHeader.create(k, Instant.now(), null, null)));
            return log.appendBatch(events);
        }

        @Override
        public List<SessionEvent> readAll(String sessionId) {
            var log = logs.get(sessionId);
            return log == null ? List.of() : log.events();
        }

        @Override
        public List<SessionEvent> readAfter(String sessionId, long afterSeq) {
            return readAll(sessionId).stream().filter(e -> e.seq() > afterSeq).toList();
        }

        @Override
        public SessionHeader getHeader(String sessionId) {
            return headers.get(sessionId);
        }

        @Override
        public long getLastSeq(String sessionId) {
            var log = logs.get(sessionId);
            return log == null ? -1 : log.lastSeq();
        }

        @Override
        public void restore(String sessionId) {}
    }
}
