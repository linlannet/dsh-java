package net.linlan.dsh.domain.sandbox.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceWritePolicyTest {

    @Test
    void followsCurrentSessionDirectory() {
        Path sessionCwd = Path.of("/repo-worktree-feature").toAbsolutePath();
        var policy = new WorkspaceWritePolicy(sessionCwd);

        assertEquals(sessionCwd.resolve("src/App.java"),
                policy.check(sessionCwd.resolve("src/App.java")));
        assertThrows(SecurityException.class,
                () -> policy.check(sessionCwd.resolve("../other/App.java")));
    }

    @Test
    void rejectsGitDirectoryAndNestedContents() {
        Path sessionCwd = Path.of("/repo-worktree-feature").toAbsolutePath();
        var policy = new WorkspaceWritePolicy(sessionCwd);

        assertThrows(SecurityException.class, () -> policy.check(sessionCwd.resolve(".git")));
        assertThrows(SecurityException.class, () -> policy.check(sessionCwd.resolve(".git/config")));
        assertThrows(SecurityException.class, () -> policy.check(sessionCwd.resolve("nested/.git/config")));
    }
}
