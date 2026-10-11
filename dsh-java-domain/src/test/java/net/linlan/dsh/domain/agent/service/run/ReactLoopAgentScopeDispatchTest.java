package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.service.Inbox;
import net.linlan.dsh.domain.agent.service.LoggingInboxNotifications;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.compaction.CompactionEngine;
import net.linlan.dsh.domain.agent.service.compaction.CompactionResult;
import net.linlan.dsh.domain.agent.service.run.tool.InMemoryToolRegistry;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopedEvent;
import net.linlan.dsh.domain.core.scope.service.ScopeEventDispatcher;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.tool.service.ToolCallExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReactLoopAgent Scope 事件分发集成测试")
class ReactLoopAgentScopeDispatchTest {

    @Test
    @DisplayName("scope.enabled=true 时事件经 ScopeEventDispatcher 分发")
    void eventsDispatchedThroughScopeEventDispatcher() throws Exception {
        var dispatcher = new ScopeEventDispatcher();
        var receivedEvents = new CopyOnWriteArrayList<ScopedEvent<?>>();
        ScopeKey sessionScope = ScopeKey.root("session:scope-test-agent");
        dispatcher.subscribe(sessionScope, receivedEvents::add);

        var agent = buildAgent("scope-test-agent");
        agent.setScopeEventDispatcher(dispatcher);

        agent.send(userMessage("Hello"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();

        assertFalse(receivedEvents.isEmpty(), "应收到 scope 事件");

        var types = receivedEvents.stream().map(ScopedEvent::type).toList();
        assertTrue(types.contains("turn/start"), "应包含 turn/start，实际: " + types);
        assertTrue(types.contains("step/start"), "应包含 step/start，实际: " + types);
        assertTrue(types.contains("assistant/message"), "应包含 assistant/message，实际: " + types);
        assertTrue(types.contains("step/end"), "应包含 step/end，实际: " + types);
        assertTrue(types.contains("turn/end"), "应包含 turn/end，实际: " + types);

        var turnStartEvent = receivedEvents.stream()
                .filter(e -> e.type().equals("turn/start"))
                .findFirst().orElseThrow();
        assertTrue(turnStartEvent.scopeKey().isDescendantOfOrEquals(sessionScope),
                "turn/start 事件 scope 应在 session scope 内");
    }

    @Test
    @DisplayName("scope.enabled=false（dispatcher=null）时退化为原行为")
    void nullDispatcherDegradesToOriginalBehavior() throws Exception {
        var agent = buildAgent("no-scope-agent");

        agent.send(userMessage("Hello"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();

        var events = agent.session().events();
        assertFalse(events.isEmpty(), "session log 应记录事件");
        var types = events.stream().map(e -> e.getClass().getSimpleName()).toList();
        assertTrue(types.stream().anyMatch(t -> t.contains("Turn")), "应包含 turn 事件");
    }

    @Test
    @DisplayName("step 级事件 scope 是 turn 级 scope 的后代")
    void stepScopeIsDescendantOfTurnScope() throws Exception {
        var dispatcher = new ScopeEventDispatcher();
        var receivedEvents = new CopyOnWriteArrayList<ScopedEvent<?>>();
        dispatcher.subscribe(ScopeKey.root("session:scope-hierarchy-agent"), receivedEvents::add);

        var agent = buildAgent("scope-hierarchy-agent");
        agent.setScopeEventDispatcher(dispatcher);

        agent.send(userMessage("Hi"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();

        var stepStart = receivedEvents.stream()
                .filter(e -> e.type().equals("step/start"))
                .findFirst().orElseThrow();
        var turnStart = receivedEvents.stream()
                .filter(e -> e.type().equals("turn/start"))
                .findFirst().orElseThrow();

        assertTrue(stepStart.scopeKey().isDescendantOf(turnStart.scopeKey()),
                "step scope 应是 turn scope 的后代");
    }

    // ── helpers ──────────────────────────────────────────

    private static Message userMessage(String text) {
        return Message.createUser(List.of(new TextBlock(text)), new MessageSource.UserSource());
    }

    private static ReactLoopAgent buildAgent(String agentId) {
        var session = new SessionLog(agentId + "-session",
                SessionHeader.create(agentId + "-session", Instant.now(), "/tmp", null));
        var inbox = new Inbox(session, LoggingInboxNotifications.INSTANCE);
        var registry = new InMemoryToolRegistry();
        var executor = new ToolCallExecutor(registry, session, 1);
        var assembler = new ScopedSystemPromptAssembler(agentId, "test-persona", List.of());
        var llm = stubLlmRuntime();
        var options = new AgentOptions(
                "test-channel", "stub-provider", "stub-model", 4096,
                null, "http://localhost", "fake-key", "openai");
        var compaction = noopCompactionEngine();

        return new ReactLoopAgent(
                agentId, options, "/tmp",
                session, inbox, llm, registry, executor,
                assembler, null, compaction);
    }

    private static ILlmRuntimePort stubLlmRuntime() {
        return new ILlmRuntimePort() {
            @Override
            public void registerAdapter(String provider, LlmAdapter adapter) {}
            @Override
            public void unregisterAdapter(String provider) {}
            @Override
            public List<LlmAdapter.LlmProviderInfo> listProviders() { return List.of(); }
            @Override
            public List<LlmAdapter.LlmModelInfo> listModels(String provider) { return List.of(); }
            @Override
            public LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model) {
                return new LlmAdapter.LlmResolvedModelInfo(
                        provider, model, model, "stub", List.of("text"),
                        new LlmAdapter.LlmModelContext(128000), 4096, null);
            }
            @Override
            public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
                SubmissionPublisher<StreamChunk> pub = new SubmissionPublisher<>();
                pub.submit(new StreamChunk.BlockStart(0, "text"));
                pub.submit(new StreamChunk.TextDelta(0, "Stub response."));
                pub.submit(new StreamChunk.BlockEnd(0, new TextBlock("Stub response.")));
                pub.submit(new StreamChunk.Usage(new TokenUsage(10, 5)));
                pub.submit(new StreamChunk.Finish(new FinishReason.Stop()));
                pub.close();
                return pub;
            }
        };
    }

    private static CompactionEngine noopCompactionEngine() {
        return new CompactionEngine() {
            @Override
            public CompletableFuture<CompactionResult> compactIfNeeded(
                    SessionLog session, AgentOptions options, CompactionTrigger trigger) {
                return CompletableFuture.completedFuture(null);
            }
            @Override
            public CompletableFuture<CompactionResult> compactNow(
                    SessionLog session, AgentOptions options) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
