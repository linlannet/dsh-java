package net.linlan.dsh.domain.session.checkpoint.service;

import net.linlan.dsh.domain.session.checkpoint.adapter.repository.ICheckpointRepository;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointRollback;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckpointStrategy 检查点策略")
class CheckpointStrategyTest {

    @Test
    @DisplayName("beforeModelRequest 写入检查点含上下文快照")
    void beforeModelRequestWritesCheckpoint() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s1");

        var snapshot = strategy.beforeModelRequest(session);

        assertEquals("s1", snapshot.sessionId());
        assertEquals(CheckpointStrategy.REASON_BEFORE_MODEL_REQUEST, snapshot.reason());
        assertNotNull(snapshot.contextSnapshotJson());
        assertTrue(snapshot.contextSnapshotJson().contains("eventCount"));
        assertEquals(1, repo.saved.size());
    }

    @Test
    @DisplayName("beforeToolSideEffect 写入可回滚检查点")
    void beforeToolSideEffectWritesCheckpoint() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s2");
        var toolCall = SessionEventFactory.toolCall(1, 1, "call-1", "write_file", "{\"path\":\"/tmp/a\"}");

        var snapshot = strategy.beforeToolSideEffect(session, toolCall);

        assertEquals("s2", snapshot.sessionId());
        assertEquals(CheckpointStrategy.REASON_BEFORE_TOOL_SIDE_EFFECT, snapshot.reason());
        assertNotNull(snapshot.contextSnapshotJson());
        assertEquals(1, repo.saved.size());
    }

    @Test
    @DisplayName("多次调用累积保存检查点")
    void multipleCallsAccumulate() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s3");

        strategy.beforeModelRequest(session);
        var toolCall = SessionEventFactory.toolCall(1, 1, "call-1", "read_file", "{}");
        strategy.beforeToolSideEffect(session, toolCall);
        strategy.beforeModelRequest(session);

        assertEquals(3, repo.saved.size());
        assertEquals(CheckpointStrategy.REASON_BEFORE_MODEL_REQUEST, repo.saved.get(0).reason());
        assertEquals(CheckpointStrategy.REASON_BEFORE_TOOL_SIDE_EFFECT, repo.saved.get(1).reason());
        assertEquals(CheckpointStrategy.REASON_BEFORE_MODEL_REQUEST, repo.saved.get(2).reason());
    }

    @Test
    @DisplayName("loadLatest 返回最近一次检查点")
    void loadLatestReturnsMostRecent() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s4");

        strategy.beforeModelRequest(session);
        var toolCall = SessionEventFactory.toolCall(1, 1, "c", "t", "{}");
        strategy.beforeToolSideEffect(session, toolCall);

        var latest = repo.loadLatest("s4");
        assertTrue(latest.isPresent());
        assertEquals(CheckpointStrategy.REASON_BEFORE_TOOL_SIDE_EFFECT, latest.get().reason());
    }

    @Test
    @DisplayName("beforeToolExecution 写入工具执行前检查点")
    void beforeToolExecutionWritesCheckpoint() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s5");

        var snapshot = strategy.beforeToolExecution(session, "write_file");

        assertEquals(CheckpointStrategy.REASON_BEFORE_TOOL_EXECUTION, snapshot.reason());
        assertEquals(1, repo.saved.size());
    }

    @Test
    @DisplayName("afterToolExecution 写入工具执行后检查点")
    void afterToolExecutionWritesCheckpoint() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s6");

        var snapshot = strategy.afterToolExecution(session, "write_file");

        assertEquals(CheckpointStrategy.REASON_AFTER_TOOL_EXECUTION, snapshot.reason());
    }

    @Test
    @DisplayName("beforeAgentTurn / afterAgentTurn 写入检查点")
    void agentTurnCheckpoints() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s7");

        var before = strategy.beforeAgentTurn(session);
        var after = strategy.afterAgentTurn(session);

        assertEquals(CheckpointStrategy.REASON_BEFORE_AGENT_TURN, before.reason());
        assertEquals(CheckpointStrategy.REASON_AFTER_AGENT_TURN, after.reason());
        assertEquals(2, repo.saved.size());
    }

    @Test
    @DisplayName("checkpoint 通用方法支持任意 reason")
    void checkpointWithCustomReason() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s8");

        var snapshot = strategy.checkpoint(session, "custom-reason");

        assertEquals("custom-reason", snapshot.reason());
    }

    @Test
    @DisplayName("rollbackTo 按 stepIndex 回滚到检查点")
    void rollbackToByStepIndex() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s9");

        var snapshot = strategy.beforeModelRequest(session);

        var rollback = strategy.rollbackTo("s9", snapshot.stepIndex());
        assertTrue(rollback.success());
        assertTrue(rollback.target().isPresent());
        assertEquals(snapshot.stepIndex(), rollback.target().get().stepIndex());
    }

    @Test
    @DisplayName("rollbackTo 不存在的 stepIndex 返回 notFound")
    void rollbackToNotFound() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);

        var rollback = strategy.rollbackTo("nonexistent", 999L);
        assertFalse(rollback.success());
        assertTrue(rollback.target().isEmpty());
    }

    @Test
    @DisplayName("rollbackToLatest 按 reason 回滚到最新检查点")
    void rollbackToLatestByReason() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s10");

        strategy.beforeModelRequest(session);
        strategy.beforeToolExecution(session, "read_file");
        strategy.beforeModelRequest(session);

        var rollback = strategy.rollbackToLatest("s10", CheckpointStrategy.REASON_BEFORE_MODEL_REQUEST);
        assertTrue(rollback.success());
        assertEquals(CheckpointStrategy.REASON_BEFORE_MODEL_REQUEST, rollback.target().get().reason());
    }

    @Test
    @DisplayName("rollbackToLatest 无 reason 回滚到最新检查点")
    void rollbackToLatestAnyReason() {
        var repo = new InMemoryCheckpointRepository();
        var strategy = new CheckpointStrategy(repo);
        var session = newSession("s11");

        strategy.beforeModelRequest(session);
        strategy.beforeToolExecution(session, "read_file");

        var rollback = strategy.rollbackToLatest("s11");
        assertTrue(rollback.success());
        assertEquals(CheckpointStrategy.REASON_BEFORE_TOOL_EXECUTION, rollback.target().get().reason());
    }

    private static SessionLog newSession(String id) {
        var header = SessionHeader.create(id, Instant.now(), "/tmp", "default");
        return new SessionLog(id, header);
    }

    private static final class InMemoryCheckpointRepository implements ICheckpointRepository {
        final List<CheckpointSnapshot> saved = new ArrayList<>();

        @Override
        public void save(CheckpointSnapshot snapshot) {
            saved.add(snapshot);
        }

        @Override
        public Optional<CheckpointSnapshot> loadLatest(String sessionId) {
            for (int i = saved.size() - 1; i >= 0; i--) {
                if (saved.get(i).sessionId().equals(sessionId)) {
                    return Optional.of(saved.get(i));
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<CheckpointSnapshot> loadLatestByReason(String sessionId, String reason) {
            for (int i = saved.size() - 1; i >= 0; i--) {
                var s = saved.get(i);
                if (s.sessionId().equals(sessionId) && s.reason().equals(reason)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<CheckpointSnapshot> loadAll(String sessionId) {
            return saved.stream().filter(s -> s.sessionId().equals(sessionId)).toList();
        }

        @Override
        public Optional<CheckpointSnapshot> loadAtStep(String sessionId, long stepIndex) {
            for (int i = saved.size() - 1; i >= 0; i--) {
                var s = saved.get(i);
                if (s.sessionId().equals(sessionId) && s.stepIndex() == stepIndex) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }
}
