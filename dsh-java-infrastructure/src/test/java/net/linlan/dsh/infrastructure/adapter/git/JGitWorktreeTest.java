package net.linlan.dsh.infrastructure.adapter.git;

import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JGitWorktreeTest {

    @Test
    void implementsGitPort() {
        JGitWorktree w = new JGitWorktree();
        assertTrue(w instanceof IGitPort);
    }

    @Test
    void defaultConstructor() {
        JGitWorktree w = new JGitWorktree();
        assertNotNull(w);
    }

    @Test
    void customConstructor() {
        JGitWorktree w = new JGitWorktree("git", 30);
        assertNotNull(w);
    }

    @Test
    void customConstructorWithCustomCommand() {
        JGitWorktree w = new JGitWorktree("/usr/local/bin/git", 120);
        assertNotNull(w);
    }

    @Test
    void currentCommitThrowsForNonGitDir() throws Exception {
        JGitWorktree w = new JGitWorktree("git", 5);
        Path temp = Files.createTempDirectory("notgit");
        try {
            assertThrows(IllegalStateException.class, () -> w.currentCommit(temp.toString()));
        } finally {
            deleteRecursively(temp);
        }
    }

    @Test
    void isCleanThrowsForNonGitDir() throws Exception {
        JGitWorktree w = new JGitWorktree("git", 5);
        Path temp = Files.createTempDirectory("notgit");
        try {
            assertThrows(IllegalStateException.class, () -> w.isClean(temp.toString()));
        } finally {
            deleteRecursively(temp);
        }
    }

    @Test
    void currentCommitReturnsHashForValidRepo() throws Exception {
        if (!isGitAvailable()) {
            return;
        }
        Path repo = Files.createTempDirectory("gitrepo");
        try {
            runGit(repo, "git", "init");
            runGit(repo, "git", "config", "user.email", "test@test.com");
            runGit(repo, "git", "config", "user.name", "Test");
            runGit(repo, "git", "commit", "--allow-empty", "-m", "init");
            JGitWorktree w = new JGitWorktree("git", 10);
            String commit = w.currentCommit(repo.toString());
            assertNotNull(commit);
            assertFalse(commit.isBlank());
            assertEquals(40, commit.trim().length());
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    void isCleanReturnsTrueForCleanRepo() throws Exception {
        if (!isGitAvailable()) {
            return;
        }
        Path repo = Files.createTempDirectory("gitrepo");
        try {
            runGit(repo, "git", "init");
            runGit(repo, "git", "config", "user.email", "test@test.com");
            runGit(repo, "git", "config", "user.name", "Test");
            runGit(repo, "git", "commit", "--allow-empty", "-m", "init");
            JGitWorktree w = new JGitWorktree("git", 10);
            assertTrue(w.isClean(repo.toString()));
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    void isCleanReturnsFalseForDirtyRepo() throws Exception {
        if (!isGitAvailable()) {
            return;
        }
        Path repo = Files.createTempDirectory("gitrepo");
        try {
            runGit(repo, "git", "init");
            runGit(repo, "git", "config", "user.email", "test@test.com");
            runGit(repo, "git", "config", "user.name", "Test");
            runGit(repo, "git", "commit", "--allow-empty", "-m", "init");
            Files.writeString(repo.resolve("untracked.txt"), "hello");
            JGitWorktree w = new JGitWorktree("git", 10);
            assertFalse(w.isClean(repo.toString()));
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    void currentCommitThrowsForGitRepoWithoutCommits() throws Exception {
        if (!isGitAvailable()) {
            return;
        }
        Path repo = Files.createTempDirectory("gitrepo");
        try {
            runGit(repo, "git", "init");
            JGitWorktree w = new JGitWorktree("git", 5);
            assertThrows(IllegalStateException.class, () -> w.currentCommit(repo.toString()));
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    void createWorktreeThrowsForNonGitDir() throws Exception {
        JGitWorktree w = new JGitWorktree("git", 5);
        Path temp = Files.createTempDirectory("notgit");
        Path target = temp.resolve("worktree-dir");
        try {
            assertThrows(IllegalStateException.class,
                    () -> w.createWorktree(temp.toString(), "branch-1", target.toString()));
        } finally {
            deleteRecursively(temp);
        }
    }

    @Test
    void createWorktreeThrowsForGitRepoWithoutCommits() throws Exception {
        if (!isGitAvailable()) {
            return;
        }
        Path repo = Files.createTempDirectory("gitrepo");
        Path target = repo.resolve("wt");
        try {
            runGit(repo, "git", "init");
            JGitWorktree w = new JGitWorktree("git", 5);
            assertThrows(IllegalStateException.class,
                    () -> w.createWorktree(repo.toString(), "new-branch", target.toString()));
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    void currentCommitThrowsForNonExistentDir() throws Exception {
        JGitWorktree w = new JGitWorktree("git", 5);
        assertThrows(IllegalStateException.class,
                () -> w.currentCommit("/nonexistent-path-xyz-123"));
    }

    @Test
    void isCleanThrowsForNonExistentDir() throws Exception {
        JGitWorktree w = new JGitWorktree("git", 5);
        assertThrows(IllegalStateException.class,
                () -> w.isClean("/nonexistent-path-xyz-123"));
    }

    private boolean isGitAvailable() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "--version").redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(3, TimeUnit.SECONDS);
        return finished && p.exitValue() == 0;
    }

    private void runGit(Path dir, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(args).directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor(10, TimeUnit.SECONDS);
    }

    private void deleteRecursively(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try { deleteRecursively(p); } catch (Exception ignored) { }
                });
            }
        }
        Files.deleteIfExists(path);
    }
}
