package net.linlan.dsh.domain.runtime.meter.service;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.runtime.meter.adapter.repository.ITokenSnapshotRepository;
import net.linlan.dsh.domain.runtime.meter.model.valobj.ReplayContext;
import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenSnapshot;
import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenUsageSummaryVO;
import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.valobj.AssistantMessagePayload;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceIntent;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T1.028 ReplayAwareTokenMeter 重放感知 Token 计量")
class ReplayAwareTokenMeterTest {

    @Test
    @DisplayName("快照不存在时全量计算并保存")
    void noSnapshotFullComputation() {
        var events = List.of(
                assistantMessage(1, 1, new TokenUsage(10, 4)),
                assistantMessage(2, 2, new TokenUsage(5, 6))
        );
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        var summary = meter.summarize("s1");

        assertEquals(2, summary.modelCalls());
        assertEquals(15, summary.total().inputTokens());
        assertEquals(10, summary.total().outputTokens());

        var saved = snapshotRepo.load("s1");
        assertTrue(saved.isPresent());
        assertEquals(2, saved.get().lastEventStepIndex());
        assertEquals(15, saved.get().totalTokens().inputTokens());
    }

    @Test
    @DisplayName("非重放时增量合流，快照前进")
    void liveIncrementalMerge() {
        var events1 = List.of(assistantMessage(1, 1, new TokenUsage(10, 4)));
        var eventLog = new StubEventLogService(events1);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        meter.summarize("s1");

        var events2 = List.of(
                assistantMessage(1, 1, new TokenUsage(10, 4)),
                assistantMessage(2, 2, new TokenUsage(20, 10))
        );
        eventLog.setEvents(events2);

        var summary = meter.summarize("s1");

        assertEquals(2, summary.modelCalls());
        assertEquals(30, summary.total().inputTokens());
        assertEquals(14, summary.total().outputTokens());

        var saved = snapshotRepo.load("s1");
        assertTrue(saved.isPresent());
        assertEquals(2, saved.get().lastEventStepIndex());
    }

    @Test
    @DisplayName("重放已含 token 的事件流，总量等于快照值不翻倍")
    void replayDoesNotDoubleCount() {
        var events = List.of(
                assistantMessage(1, 1, new TokenUsage(10, 4)),
                assistantMessage(2, 2, new TokenUsage(5, 6))
        );
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        meter.summarize("s1");

        var replaySummary = meter.summarize("s1", ReplayContext.replay(2));

        assertEquals(2, replaySummary.modelCalls());
        assertEquals(15, replaySummary.total().inputTokens());
        assertEquals(10, replaySummary.total().outputTokens());
    }

    @Test
    @DisplayName("重放时从快照恢复不重新估算")
    void replayRestoresFromSnapshot() {
        var events = List.of(
                assistantMessage(1, 1, new TokenUsage(100, 50))
        );
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        meter.summarize("s1");

        eventLog.setEvents(List.of(
                assistantMessage(1, 1, new TokenUsage(100, 50)),
                assistantMessage(2, 2, new TokenUsage(999, 999))
        ));

        var replaySummary = meter.summarize("s1", ReplayContext.replay(1));

        assertEquals(1, replaySummary.modelCalls());
        assertEquals(100, replaySummary.total().inputTokens());
        assertEquals(50, replaySummary.total().outputTokens());
    }

    @Test
    @DisplayName("重放但快照不存在时退化为全量计算")
    void replayWithoutSnapshotFallsBackToFull() {
        var events = List.of(
                assistantMessage(1, 1, new TokenUsage(10, 4))
        );
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        var summary = meter.summarize("s1", ReplayContext.replay(1));

        assertEquals(1, summary.modelCalls());
        assertEquals(10, summary.total().inputTokens());
    }

    @Test
    @DisplayName("无新事件时 summarize 幂等")
    void idempotentWhenNoNewEvents() {
        var events = List.of(assistantMessage(1, 1, new TokenUsage(10, 4)));
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        var s1 = meter.summarize("s1");
        var s2 = meter.summarize("s1");

        assertEquals(s1.modelCalls(), s2.modelCalls());
        assertEquals(s1.total().inputTokens(), s2.total().inputTokens());
        assertEquals(s1.total().outputTokens(), s2.total().outputTokens());
    }

    @Test
    @DisplayName("部分重放：快照含 3 条事件，重放到 step 2 返回快照值")
    void partialReplay() {
        var events = List.of(
                assistantMessage(1, 1, new TokenUsage(10, 4)),
                assistantMessage(2, 2, new TokenUsage(20, 10)),
                assistantMessage(3, 3, new TokenUsage(30, 20))
        );
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        meter.summarize("s1");

        var partialReplay = meter.summarize("s1", ReplayContext.replay(2));

        assertEquals(3, partialReplay.modelCalls());
        assertEquals(60, partialReplay.total().inputTokens());
        assertEquals(34, partialReplay.total().outputTokens());
    }

    @Test
    @DisplayName("与 v0.1.8 SessionTokenMeterService 全量重算结果一致")
    void consistentWithLegacyFullRecompute() {
        var events = List.of(
                assistantMessage(1, 1, new TokenUsage(10, 4, 2, 1, 3)),
                assistantMessage(2, 2, new TokenUsage(5, 6)),
                assistantMessage(3, 3, new TokenUsage(15, 8, 1, 0, 2))
        );
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        var legacyService = new SessionTokenMeterService(eventLog);
        var legacyResult = legacyService.summarize("s1");

        var replayResult = meter.summarize("s1");

        assertEquals(legacyResult.modelCalls(), replayResult.modelCalls());
        assertEquals(legacyResult.total().inputTokens(), replayResult.total().inputTokens());
        assertEquals(legacyResult.total().outputTokens(), replayResult.total().outputTokens());
        assertEquals(legacyResult.total().cacheReadTokens(), replayResult.total().cacheReadTokens());
        assertEquals(legacyResult.total().cacheWriteTokens(), replayResult.total().cacheWriteTokens());
        assertEquals(legacyResult.total().reasoningTokens(), replayResult.total().reasoningTokens());
    }

    @Test
    @DisplayName("summarize(sessionId) 等价于 summarize(sessionId, live())")
    void defaultSummarizeEqualsLive() {
        var events = List.of(assistantMessage(1, 1, new TokenUsage(10, 4)));
        var eventLog = new StubEventLogService(events);
        var snapshotRepo = new InMemorySnapshotRepository();
        var meter = new ReplayAwareTokenMeter(eventLog, snapshotRepo);

        var s1 = meter.summarize("s1");

        eventLog.setEvents(List.of(assistantMessage(1, 1, new TokenUsage(10, 4))));
        snapshotRepo.store.clear();

        var s2 = meter.summarize("s1", ReplayContext.live());

        assertEquals(s1.modelCalls(), s2.modelCalls());
        assertEquals(s1.total().inputTokens(), s2.total().inputTokens());
    }

    // ── Helpers ───────────────────────────────────────────────

    private static SessionEvent assistantMessage(long seq, long step, TokenUsage usage) {
        return new SessionEvent.AssistantMessage(
                seq,
                Instant.now(),
                new AssistantMessagePayload(
                        1,
                        step,
                        Message.createAssistant(
                                List.of(new TextBlock("text")),
                                new MessageSource.ModelMessageSource("p", "m")
                        ),
                        usage
                ),
                new SurfaceIntent(new SurfaceOp.Append(), null)
        );
    }

    // ── Stubs ─────────────────────────────────────────────────

    private static final class StubEventLogService implements ISessionEventLogService {
        private List<SessionEvent> events;

        StubEventLogService(List<SessionEvent> events) {
            this.events = events;
        }

        void setEvents(List<SessionEvent> events) {
            this.events = events;
        }

        @Override public SessionHeader createSession(SessionHeader header) { return header; }
        @Override public SessionEvent append(String sessionId, SessionEvent event) { return event; }
        @Override public List<SessionEvent> appendBatch(String sessionId, List<SessionEvent> evts) { return evts; }
        @Override public List<SessionEvent> readAll(String sessionId) { return new ArrayList<>(events); }
        @Override public List<SessionEvent> readAfter(String sessionId, long afterSeq) {
            List<SessionEvent> result = new ArrayList<>();
            for (SessionEvent e : events) {
                if (e.seq() > afterSeq) result.add(e);
            }
            return result;
        }
        @Override public SessionHeader getHeader(String sessionId) { return null; }
        @Override public long getLastSeq(String sessionId) { return events.isEmpty() ? 0 : events.get(events.size() - 1).seq(); }
        @Override public void restore(String sessionId) {}
    }

    private static final class InMemorySnapshotRepository implements ITokenSnapshotRepository {
        final Map<String, TokenSnapshot> store = new HashMap<>();

        @Override public Optional<TokenSnapshot> load(String sessionId) {
            return Optional.ofNullable(store.get(sessionId));
        }

        @Override public void save(String sessionId, TokenSnapshot snapshot) {
            store.put(sessionId, snapshot);
        }
    }
}
