package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterWorktreeToolTest {

    @Test
    void createsThenEntersSameNamedWorktree() throws Exception {
        Path repo = Files.createTempDirectory("enter-worktree-test");
        Files.createDirectories(repo.resolve(".git"));
        CapturingEventStore store = new CapturingEventStore();
        FakeGitPort git = new FakeGitPort();
        AtomicReference<String> cwd = new AtomicReference<>(repo.toString());
        var tool = new EnterWorktreeTool(git, store, "session-1", cwd);

        var created = tool.enter(repo.toString(), "feature");
        var entered = tool.enter(repo.toString(), "feature");

        assertEquals(repo + "-worktree-feature", created.path());
        assertEquals(created.path(), entered.path());
        assertTrue(Files.isDirectory(Path.of(created.path())));
        assertEquals(created.path(), cwd.get());
        assertEquals(1, git.calls);
        assertEquals(repo.toString(), git.repoPath);
        assertEquals("feature", git.branch);
        assertEquals(created.path(), git.targetPath);
        assertEquals(1, store.events.size());
    }

    @Test
    void exposesToolDefinitionAndRejectsInvalidArguments() {
        var tool = new EnterWorktreeTool();

        assertEquals("enter_worktree", tool.name());
        assertTrue(tool.parameters().containsKey("required"));
        var result = tool.execute(java.util.Map.of(), null).join();
        assertTrue(result.isError());
        assertEquals("INVALID_ARGS", ((ToolExecutionResult.Failure) result).error().info().code());
    }

    @Test
    void nonGitRepositoryIsRejected() {
        assertThrows(EnterWorktreeTool.NotGitRepoException.class,
                () -> new EnterWorktreeTool().enter("/tmp/nonexistent-repo", "feature"));
    }

    private static final class FakeGitPort implements IGitPort {
        int calls;
        String repoPath;
        String branch;
        String targetPath;

        @Override
        public String createWorktree(String repoPath, String branch, String targetPath) {
            calls++;
            this.repoPath = repoPath;
            this.branch = branch;
            this.targetPath = targetPath;
            try {
                Files.createDirectories(Path.of(targetPath));
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
            return targetPath;
        }

        @Override
        public String currentCommit(String repoPath) {
            return "commit";
        }

        @Override
        public boolean isClean(String repoPath) {
            return true;
        }
    }

    private static final class CapturingEventStore implements ISessionEventStore {
        final List<SessionEvent> events = new ArrayList<>();

        @Override public void saveHeader(SessionHeader header) { }
        @Override public Optional<SessionHeader> getHeader(String sessionId) { return Optional.empty(); }
        @Override public void append(String sessionId, SessionEvent event) { events.add(event); }
        @Override public void appendBatch(String sessionId, List<SessionEvent> batch) { events.addAll(batch); }
        @Override public List<SessionEvent> readAll(String sessionId) { return List.copyOf(events); }
        @Override public List<SessionEvent> readAfter(String sessionId, long afterSeq) { return List.of(); }
        @Override public List<SessionEvent> readRange(String sessionId, long fromSeq, long toSeq) { return List.of(); }
        @Override public long getLastSeq(String sessionId) { return events.size() - 1L; }
        @Override public long count(String sessionId) { return events.size(); }
        @Override public List<SessionHeader> listHeaders(int limit) { return List.of(); }
    }
}
