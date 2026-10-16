package net.linlan.dsh.domain.tool.worktree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("worktree-test");
        java.nio.file.Files.createDirectories(tmp.resolve(".git"));
        var result = tool.create(tmp.toString(), "feature");
        assertNotNull(result.path());
        assertEquals("feature", result.branch());
    }
}
