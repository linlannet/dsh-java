package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WorktreeTool Git 工作树")
class WorktreeToolTest {

    @Test
    @DisplayName("非 Git 仓库抛异常")
    void notGitRepo() {
        var tool = new WorktreeTool();
        assertThrows(WorktreeTool.NotGitRepoException.class,
                () -> tool.create("/tmp/nonexistent-repo", "feature"));
    }

    @Test
    @DisplayName("Git 仓库创建工作树")
    void createWorktree() throws Exception {
        var tool = new WorktreeTool();
        Path tmp = Files.createTempDirectory("worktree-test");
        Files.createDirectories(tmp.resolve(".git"));
        var result = tool.create(tmp.toString(), "feature");
        assertNotNull(result.path());
        assertEquals("feature", result.branch());
    }

    @Test
    @DisplayName("创建成功后写入 plugin:worktree/created 事件")
    void writesCreatedEvent() throws Exception {
        var store = new CapturingEventStore();
        var tool = new WorktreeTool(store);
        Path tmp = Files.createTempDirectory("worktree-event-test");
        Files.createDirectories(tmp.resolve(".git"));

        tool.create(tmp.toString(), "feature", "session-1");

        assertEquals(1, store.events.size());
        SessionEvent event = store.events.get(0);
        assertEquals(SessionEventType.WORKTREE_CREATED, event.type());
        assertEquals(6L, event.seq());
        assertTrue(((SessionEvent.Generic) event).payload().contains("\"branch\":\"feature\""));
    }

    @Test
    @DisplayName("无会话时不写事件")
    void skipsEventWithoutSession() throws Exception {
        var store = new CapturingEventStore();
        var tool = new WorktreeTool(store);
        Path tmp = Files.createTempDirectory("worktree-no-session");
        Files.createDirectories(tmp.resolve(".git"));

        tool.create(tmp.toString(), "feature");

        assertTrue(store.events.isEmpty());
    }

    private static final class CapturingEventStore implements ISessionEventStore {
        final List<SessionEvent> events = new ArrayList<>();

        @Override
        public void saveHeader(SessionHeader header) {
        }

        @Override
        public Optional<SessionHeader> getHeader(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void append(String sessionId, SessionEvent event) {
            events.add(event);
        }

        @Override
        public void appendBatch(String sessionId, List<SessionEvent> batch) {
            events.addAll(batch);
        }

        @Override
        public List<SessionEvent> readAll(String sessionId) {
            return List.copyOf(events);
        }

        @Override
        public List<SessionEvent> readAfter(String sessionId, long afterSeq) {
            return List.of();
        }

        @Override
        public List<SessionEvent> readRange(String sessionId, long fromSeq, long toSeq) {
            return List.of();
        }

        @Override
        public long getLastSeq(String sessionId) {
            return 5L;
        }

        @Override
        public long count(String sessionId) {
            return events.size();
        }

        @Override
        public List<SessionHeader> listHeaders(int limit) {
            return List.of();
        }
    }
}
