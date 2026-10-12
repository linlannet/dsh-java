package net.linlan.dsh.domain.storage.deliverable;

import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.036 DeliverableTool 与 GitWorktreeSnapshot")
class DeliverableToolTest {

    @Test
    @DisplayName("declareDeliverable 返回含名称路径的 Deliverable")
    void declareReturnsDeliverable() {
        var tool = new DeliverableTool();
        var d = tool.declareDeliverable("report", "/out/report.pdf", "final report");
        assertEquals("report", d.name());
        assertEquals("/out/report.pdf", d.path());
        assertEquals("final report", d.description());
        assertNotNull(d.declaredAt());
    }

    @Test
    @DisplayName("declareDeliverable 空名称抛异常")
    void declareBlankNameThrows() {
        var tool = new DeliverableTool();
        assertThrows(IllegalArgumentException.class, () -> tool.declareDeliverable("", "/p", "d"));
    }

    @Test
    @DisplayName("declareDeliverable 空路径抛异常")
    void declareBlankPathThrows() {
        var tool = new DeliverableTool();
        assertThrows(IllegalArgumentException.class, () -> tool.declareDeliverable("n", "", "d"));
    }

    @Test
    @DisplayName("listDeliverables 返回所有已声明交付物")
    void listDeliverables() {
        var tool = new DeliverableTool();
        tool.declareDeliverable("a", "/a", "da");
        tool.declareDeliverable("b", "/b", "db");
        assertEquals(2, tool.listDeliverables().size());
    }

    @Test
    @DisplayName("findByName 按名称查找交付物")
    void findByName() {
        var tool = new DeliverableTool();
        tool.declareDeliverable("a", "/a", "da");
        var found = tool.findByName("a");
        assertNotNull(found);
        assertEquals("/a", found.path());
        assertNull(tool.findByName("nonexistent"));
    }

    @Test
    @DisplayName("remove 按名称移除交付物")
    void removeByName() {
        var tool = new DeliverableTool();
        tool.declareDeliverable("a", "/a", "da");
        assertTrue(tool.remove("a"));
        assertEquals(0, tool.count());
        assertFalse(tool.remove("nonexistent"));
    }

    @Test
    @DisplayName("GitWorktreeSnapshot.snapshot 返回快照路径")
    void snapshotReturnsPath() {
        var port = new StubGitPort("/repo/.snapshots/feature", "abc123");
        var snapshot = new GitWorktreeSnapshot(port);
        String path = snapshot.snapshot("/repo", "feature");
        assertNotNull(path);
        assertTrue(path.contains("feature"));
    }

    @Test
    @DisplayName("GitWorktreeSnapshot.snapshotWithPath 返回含 commit 的结果")
    void snapshotWithPathReturnsCommit() {
        var port = new StubGitPort("/repo/.snapshots/feature", "abc123");
        var snapshot = new GitWorktreeSnapshot(port);
        var result = snapshot.snapshotWithPath("/repo", "feature");
        assertEquals("feature", result.branch());
        assertEquals("abc123", result.commit());
    }

    @Test
    @DisplayName("snapshot 空仓库路径抛异常")
    void snapshotBlankRepoThrows() {
        var snapshot = new GitWorktreeSnapshot(new StubGitPort("", ""));
        assertThrows(IllegalArgumentException.class, () -> snapshot.snapshot("", "branch"));
    }

    @Test
    @DisplayName("snapshot 空分支抛异常")
    void snapshotBlankBranchThrows() {
        var snapshot = new GitWorktreeSnapshot(new StubGitPort("", ""));
        assertThrows(IllegalArgumentException.class, () -> snapshot.snapshot("/repo", ""));
    }

    @Test
    @DisplayName("null gitPort 抛异常")
    void nullGitPortThrows() {
        assertThrows(NullPointerException.class, () -> new GitWorktreeSnapshot(null));
    }

    static final class StubGitPort implements IGitPort {
        final String worktreeResult;
        final String commitResult;

        StubGitPort(String worktreeResult, String commitResult) {
            this.worktreeResult = worktreeResult;
            this.commitResult = commitResult;
        }

        @Override
        public String createWorktree(String repoPath, String branch, String targetPath) {
            return targetPath;
        }

        @Override
        public String currentCommit(String repoPath) {
            return commitResult;
        }

        @Override
        public boolean isClean(String repoPath) {
            return true;
        }
    }
}
