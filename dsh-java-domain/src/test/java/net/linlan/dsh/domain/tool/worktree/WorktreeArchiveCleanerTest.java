package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.tool.worktree.adapter.port.IWorktreeCleanupPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Worktree 归档清理")
class WorktreeArchiveCleanerTest {

    private static final String REPO = "/repo";
    private static final String WORKTREE = "/repo-worktree-feature";

    @Test
    @DisplayName("retainOnArchive=true 时保留工作树")
    void retainsWhenConfigured() {
        var port = new FakePort(cleanState(), true);
        var result = new WorktreeArchiveCleaner(port)
                .archive(REPO, WORKTREE, new WorktreeArchivePolicy(true));

        assertEquals("skipped", result.outcome());
        assertFalse(port.removed);
    }

    @Test
    @DisplayName("干净工作树非强制移除并保留分支")
    void removesCleanWorktree() {
        var port = new FakePort(cleanState(), true);
        var result = new WorktreeArchiveCleaner(port).archive(REPO, WORKTREE, null);

        assertEquals("removed", result.outcome());
        assertTrue(port.removed);
        assertTrue(result.message().contains("feature"));
    }

    @Test
    @DisplayName("脏树、子模块、锁、分离 HEAD、路径缺失均跳过")
    void skipsUnsafeStates() {
        assertSkipped(new IWorktreeCleanupPort.WorktreeState(
                false, false, false, false, false, null), "not found");
        assertSkipped(new IWorktreeCleanupPort.WorktreeState(
                true, false, false, false, false, "feature"), "uncommitted");
        assertSkipped(new IWorktreeCleanupPort.WorktreeState(
                true, true, true, false, false, "feature"), "submodules");
        assertSkipped(new IWorktreeCleanupPort.WorktreeState(
                true, true, false, true, false, "feature"), "locked");
        assertSkipped(new IWorktreeCleanupPort.WorktreeState(
                true, true, false, false, true, null), "detached");
    }

    @Test
    @DisplayName("移除失败返回 failed 且不抛异常")
    void removalFailureIsReported() {
        var port = new FakePort(cleanState(), false);
        var result = new WorktreeArchiveCleaner(port).archive(REPO, WORKTREE, null);

        assertEquals("failed", result.outcome());
        assertEquals("remove failed", result.message());
    }

    private static void assertSkipped(IWorktreeCleanupPort.WorktreeState state, String reason) {
        var port = new FakePort(state, true);
        var result = new WorktreeArchiveCleaner(port).archive(REPO, WORKTREE, null);

        assertEquals("skipped", result.outcome());
        assertTrue(result.message().contains(reason), result.message());
        assertFalse(port.removed);
    }

    private static IWorktreeCleanupPort.WorktreeState cleanState() {
        return new IWorktreeCleanupPort.WorktreeState(
                true, true, false, false, false, "feature");
    }

    private static final class FakePort implements IWorktreeCleanupPort {
        private final WorktreeState state;
        private final boolean removeSucceeds;
        boolean removed;

        FakePort(WorktreeState state, boolean removeSucceeds) {
            this.state = state;
            this.removeSucceeds = removeSucceeds;
        }

        @Override
        public WorktreeState inspect(String repoPath, String worktreePath) {
            return state;
        }

        @Override
        public RemovalResult removeWorktree(String repoPath, String worktreePath) {
            removed = true;
            return new RemovalResult(removeSucceeds, removeSucceeds ? "removed" : "remove failed");
        }
    }
}
