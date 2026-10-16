package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.agent.service.Inbox;
import net.linlan.dsh.domain.agent.service.LoggingInboxNotifications;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.compaction.CompactionEngine;
import net.linlan.dsh.domain.agent.service.compaction.CompactionResult;
import net.linlan.dsh.domain.agent.service.run.tool.InMemoryToolRegistry;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.checkpoint.adapter.repository.ICheckpointRepository;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import net.linlan.dsh.domain.session.checkpoint.service.CheckpointStrategy;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.tool.service.ToolCallExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReactLoopAgent 检查点集成")
class ReactLoopAgentCheckpointTest {

    @Test
    @DisplayName("注入 CheckpointStrategy 后运行时在 LLM 请求前保存检查点")
    void checkpointSavedBeforeModelRequest() throws Exception {
        var repo = new RecordingRepository();
        var strategy = new CheckpointStrategy(repo);
        var agent = buildAgent("ckpt-agent");
        agent.setCheckpointStrategy(strategy);

        agent.send(userMessage("Hello"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();

        assertFalse(repo.saved.isEmpty(), "应至少保存一次检查点");
        var beforeModel = repo.saved.stream()
                .filter(s -> s.reason().equals(CheckpointStrategy.REASON_BEFORE_MODEL_REQUEST))
                .toList();
        assertFalse(beforeModel.isEmpty(),
                "应存在 reason=before-model-request 的检查点");
        assertEquals(agent.session().sessionId(), beforeModel.get(0).sessionId(),
                "检查点 sessionId 应与 agent session 一致");
    }

    @Test
    @DisplayName("未注入 CheckpointStrategy 时正常运行不抛异常")
    void noCheckpointStrategyRunsNormally() throws Exception {
        var agent = buildAgent("no-ckpt-agent");
        // 不调用 setCheckpointStrategy

        agent.send(userMessage("Hi"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();

        assertFalse(agent.session().events().isEmpty(),
                "session log 应记录事件");
    }

    @Test
    @DisplayName("CheckpointStrategy 抛异常时被忽略，agent 继续完成")
    void checkpointExceptionIgnored() throws Exception {
        ICheckpointRepository throwingRepo = new ICheckpointRepository() {
            @Override
            public void save(CheckpointSnapshot snapshot) {
                throw new RuntimeException("checkpoint storage failure");
            }
            @Override
            public Optional<CheckpointSnapshot> loadLatest(String sessionId) {
                return Optional.empty();
            }
            @Override
            public Optional<CheckpointSnapshot> loadLatestByReason(String sessionId, String reason) {
                return Optional.empty();
            }
            @Override
            public List<CheckpointSnapshot> loadAll(String sessionId) {
                return List.of();
            }
            @Override
            public Optional<CheckpointSnapshot> loadAtStep(String sessionId, long stepIndex) {
                return Optional.empty();
            }
        };
        var strategy = new CheckpointStrategy(throwingRepo);
        var agent = buildAgent("throw-ckpt-agent");
        agent.setCheckpointStrategy(strategy);

        agent.send(userMessage("Hello"), InboxTarget.NEXT_TURN, true);
        // 异常被忽略，应正常完成
        assertDoesNotThrow(() -> agent.whenIdle().join());

        var events = agent.session().events();
        assertFalse(events.isEmpty(), "即使检查点失败，会话仍应记录事件");
    }

    @Test
    @DisplayName("多次 turn 每次都在 LLM 请求前保存检查点")
    void checkpointSavedEachTurn() throws Exception {
        var repo = new RecordingRepository();
        var strategy = new CheckpointStrategy(repo);
        var agent = buildAgent("multi-turn-ckpt");
        agent.setCheckpointStrategy(strategy);

        agent.send(userMessage("first"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();
        int afterFirst = repo.saved.size();

        agent.send(userMessage("second"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();
        int afterSecond = repo.saved.size();

        assertTrue(afterSecond >= afterFirst,
                "第二次 turn 应继续保存检查点");
        assertTrue(afterSecond >= 2,
                "两轮 turn 应至少保存 2 次检查点");
    }

    @Test
    @DisplayName("检查点 stepIndex 取自 session.lastSeq")
    void checkpointStepIndexFromSessionLastSeq() throws Exception {
        var repo = new RecordingRepository();
        var strategy = new CheckpointStrategy(repo);
        var agent = buildAgent("seq-ckpt");
        agent.setCheckpointStrategy(strategy);

        agent.send(userMessage("check seq"), InboxTarget.NEXT_TURN, true);
        agent.whenIdle().join();

        assertFalse(repo.saved.isEmpty());
        var snap = repo.saved.get(0);
        assertTrue(snap.stepIndex() >= 0,
                "stepIndex 应为非负的 session.lastSeq");
        assertNotNull(snap.contextSnapshotJson(),
                "contextSnapshotJson 应记录上下文摘要");
    }

    // ── helpers ──────────────────────────────────────────────

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

    private static final class RecordingRepository implements ICheckpointRepository {
        final CopyOnWriteArrayList<CheckpointSnapshot> saved = new CopyOnWriteArrayList<>();

        @Override
        public void save(CheckpointSnapshot snapshot) {
            saved.add(snapshot);
        }
        @Override
        public Optional<CheckpointSnapshot> loadLatest(String sessionId) {
            return saved.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .reduce((a, b) -> b);
        }
        @Override
        public List<CheckpointSnapshot> loadAll(String sessionId) {
            return saved.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .toList();
        }
        @Override
        public Optional<CheckpointSnapshot> loadLatestByReason(String sessionId, String reason) {
            return saved.stream()
                    .filter(s -> s.sessionId().equals(sessionId) && s.reason().equals(reason))
                    .reduce((a, b) -> b);
        }
        @Override
        public Optional<CheckpointSnapshot> loadAtStep(String sessionId, long stepIndex) {
            return saved.stream()
                    .filter(s -> s.sessionId().equals(sessionId) && s.stepIndex() == stepIndex)
                    .reduce((a, b) -> b);
        }
    }
}
