package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.service.LlmGenerationService;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BasicCompactionEngine 基础上下文压缩引擎")
class BasicCompactionEngineTest {

    @Test
    @DisplayName("compactIfNeeded：消息数不足 minMessagesToCompact 时返回 null")
    void compactIfNeededTooFewMessages() throws Exception {
        var session = newSession("s-too-few");
        appendUser(session, "hi");
        var engine = new BasicCompactionEngine(stubLlm("summary"), 1000L, 5);

        var result = engine.compactIfNeeded(session, defaultOptions(),
                CompactionEngine.CompactionTrigger.PRESSURE).get();
        assertNull(result, "消息数 < minMessagesToCompact 不应压缩");
    }

    @Test
    @DisplayName("compactIfNeeded：token 未达阈值且 trigger=PRESSURE 时返回 null")
    void compactIfNeededBelowThresholdPressure() throws Exception {
        var session = newSession("s-low-token");
        appendUser(session, "short");
        appendUser(session, "msg");
        var engine = new BasicCompactionEngine(stubLlm("summary"), 10_000L, 2);

        var result = engine.compactIfNeeded(session, defaultOptions(),
                CompactionEngine.CompactionTrigger.PRESSURE).get();
        assertNull(result, "token 低于阈值且 PRESSURE 不应压缩");
    }

    @Test
    @DisplayName("compactIfNeeded：trigger=CONTEXT_OVERFLOW 强制触发压缩")
    void compactIfNeededContextOverflowForces() throws Exception {
        var session = newSession("s-overflow");
        appendUser(session, "first message body");
        appendUser(session, "second message body");
        appendUser(session, "third message body");
        appendUser(session, "fourth message body");
        var engine = new BasicCompactionEngine(stubLlm("compacted summary"), 10_000L, 2);

        var result = engine.compactIfNeeded(session, defaultOptions(),
                CompactionEngine.CompactionTrigger.CONTEXT_OVERFLOW).get();
        assertNotNull(result, "CONTEXT_OVERFLOW 应强制压缩");
        assertEquals("compacted summary", result.summary());
        assertTrue(result.tokensBefore() >= result.tokensAfter(),
                "压缩后 token 应不多于压缩前");
    }

    @Test
    @DisplayName("compactIfNeeded：token 达阈值且 trigger=PRESSURE 触发压缩")
    void compactIfNeededThresholdReached() throws Exception {
        var session = newSession("s-threshold");
        appendUser(session, "a long enough message body to exceed threshold");
        appendUser(session, "another long enough message body to exceed threshold");
        appendUser(session, "third long enough message body to exceed threshold");
        appendUser(session, "fourth long enough message body to exceed threshold");
        var engine = new BasicCompactionEngine(stubLlm("merged summary"), 1L, 2);

        var result = engine.compactIfNeeded(session, defaultOptions(),
                CompactionEngine.CompactionTrigger.PRESSURE).get();
        assertNotNull(result);
        assertEquals("merged summary", result.summary());
    }

    @Test
    @DisplayName("compactNow：消息数 < 2 返回 null")
    void compactNowTooFewMessages() throws Exception {
        var session = newSession("s-single");
        appendUser(session, "only one");
        var engine = new BasicCompactionEngine(stubLlm("summary"), 1L, 1);

        var result = engine.compactNow(session, defaultOptions()).get();
        assertNull(result);
    }

    @Test
    @DisplayName("compactNow：正常压缩返回 CompactionResult 并写入替换事件")
    void compactNowReturnsResultAndAppendsReplace() throws Exception {
        var session = newSession("s-normal");
        appendUser(session, "first body content");
        appendUser(session, "second body content");
        appendUser(session, "third body content");
        appendUser(session, "fourth body content");
        var engine = new BasicCompactionEngine(stubLlm("concise summary"), 1L, 2);

        int eventsBefore = session.size();
        var result = engine.compactNow(session, defaultOptions()).get();
        assertNotNull(result);
        assertEquals("concise summary", result.summary());
        assertTrue(result.startSeq() >= 0, "startSeq 应为有效事件序号");
        assertTrue(result.endSeq() >= result.startSeq(),
                "endSeq 应 >= startSeq");
        assertNotNull(result.summaryMessage());
        assertFalse(result.replacedMessages().isEmpty(),
                "replacedMessages 应记录被压缩的原始消息");
        assertTrue(session.size() > eventsBefore,
                "压缩后应追加替换事件");
    }

    @Test
    @DisplayName("compactNow：摘要为空时返回 null")
    void compactNowBlankSummaryReturnsNull() throws Exception {
        var session = newSession("s-blank");
        appendUser(session, "first body content");
        appendUser(session, "second body content");
        appendUser(session, "third body content");
        appendUser(session, "fourth body content");
        var engine = new BasicCompactionEngine(stubLlm("   "), 1L, 2);

        var result = engine.compactNow(session, defaultOptions()).get();
        assertNull(result, "空摘要不应产生 CompactionResult");
    }

    @Test
    @DisplayName("compactNow：LLM 返回 null 时返回 null")
    void compactNowNullResponseReturnsNull() throws Exception {
        var session = newSession("s-null-resp");
        appendUser(session, "first body content");
        appendUser(session, "second body content");
        appendUser(session, "third body content");
        appendUser(session, "fourth body content");
        var engine = new BasicCompactionEngine(stubLlm(null), 1L, 2);

        var result = engine.compactNow(session, defaultOptions()).get();
        assertNull(result);
    }

    @Test
    @DisplayName("CompactionTrigger 枚举包含 PRESSURE 与 CONTEXT_OVERFLOW")
    void triggerEnumValues() {
        var values = CompactionEngine.CompactionTrigger.values();
        assertEquals(2, values.length);
        assertSame(CompactionEngine.CompactionTrigger.PRESSURE, values[0]);
        assertSame(CompactionEngine.CompactionTrigger.CONTEXT_OVERFLOW, values[1]);
    }

    // ── helpers ──────────────────────────────────────────────

    private static SessionLog newSession(String id) {
        return new SessionLog(id, SessionHeader.create(id, Instant.now(), "/tmp", null));
    }

    private static void appendUser(SessionLog session, String text) {
        var msg = Message.createUser(List.of(new TextBlock(text)), new MessageSource.UserSource());
        session.append(SessionEventFactory.userMessage(0, 0, msg));
    }

    private static AgentOptions defaultOptions() {
        return new AgentOptions(
                "test-channel", "stub-provider", "stub-model", 4096,
                null, "http://localhost", "fake-key", "openai");
    }

    private static LlmGenerationService stubLlm(String summaryText) {
        return new LlmGenerationService(stubRuntime()) {
            @Override
            public CompletableFuture<Message> generate(GenerateOptions options) {
                if (summaryText == null) {
                    return CompletableFuture.completedFuture(null);
                }
                var msg = Message.createUser(
                        List.of(new TextBlock(summaryText)),
                        new MessageSource.UserSource());
                return CompletableFuture.completedFuture(msg);
            }
        };
    }

    private static ILlmRuntimePort stubRuntime() {
        return new ILlmRuntimePort() {
            @Override public void registerAdapter(String provider, LlmAdapter adapter) {}
            @Override public void unregisterAdapter(String provider) {}
            @Override public List<LlmAdapter.LlmProviderInfo> listProviders() { return List.of(); }
            @Override public List<LlmAdapter.LlmModelInfo> listModels(String provider) { return List.of(); }
            @Override public LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model) { return null; }
            @Override public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
                return new Flow.Publisher<>() {
                    @Override public void subscribe(Flow.Subscriber<? super StreamChunk> subscriber) {
                        subscriber.onSubscribe(new Flow.Subscription() {
                            @Override public void request(long n) { subscriber.onComplete(); }
                            @Override public void cancel() {}
                        });
                    }
                };
            }
        };
    }
}
