package net.linlan.dsh.domain.session.checkpoint.service;

import net.linlan.dsh.domain.session.checkpoint.adapter.repository.ICheckpointRepository;
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
        public List<CheckpointSnapshot> loadAll(String sessionId) {
            return saved.stream().filter(s -> s.sessionId().equals(sessionId)).toList();
        }
    }
}
