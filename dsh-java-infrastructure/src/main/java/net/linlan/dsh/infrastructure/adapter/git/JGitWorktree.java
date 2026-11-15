package net.linlan.dsh.infrastructure.adapter.git;

import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import net.linlan.dsh.domain.tool.worktree.adapter.port.IWorktreeCleanupPort;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class JGitWorktree implements IGitPort, IWorktreeCleanupPort {

    private final String gitCommand;
    private final long timeoutSeconds;

    public JGitWorktree() {
        this("git", 60L);
    }

    public JGitWorktree(String gitCommand, long timeoutSeconds) {
        this.gitCommand = gitCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String createWorktree(String repoPath, String branch, String targetPath) {
        File target = new File(targetPath);
        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
            throw new IllegalStateException("cannot create parent dir for: " + targetPath);
        }
        runCommand(repoPath, gitCommand, "worktree", "add", "-b", branch, targetPath, "HEAD");
        return targetPath;
    }

    @Override
    public String currentCommit(String repoPath) {
        return runCommand(repoPath, gitCommand, "rev-parse", "HEAD").trim();
    }

    @Override
    public boolean isClean(String repoPath) {
        String status = runCommand(repoPath, gitCommand, "status", "--porcelain");
        return status == null || status.isBlank();
    }

    @Override
    public WorktreeState inspect(String repoPath, String worktreePath) {
        File worktreeDir = new File(worktreePath);
        if (!worktreeDir.exists()) {
            return new WorktreeState(false, false, false, false, false, null);
        }
        boolean clean;
        try {
            clean = isClean(worktreePath);
        } catch (RuntimeException e) {
            clean = false;
        }
        GitOutput submodules = runAllowFailure(worktreePath, gitCommand, "submodule", "status");
        GitOutput head = runAllowFailure(worktreePath, gitCommand, "symbolic-ref", "--short", "HEAD");
        boolean detached = head.exitCode() != 0;
        return new WorktreeState(
                true,
                clean,
                !submodules.output().isBlank(),
                isLocked(repoPath, worktreePath),
                detached,
                detached ? null : head.output().trim()
        );
    }

    @Override
    public RemovalResult removeWorktree(String repoPath, String worktreePath) {
        try {
            runCommand(repoPath, gitCommand, "worktree", "remove", worktreePath);
            return new RemovalResult(true, "worktree removed");
        } catch (RuntimeException e) {
            return new RemovalResult(false, e.getMessage());
        }
    }

    private boolean isLocked(String repoPath, String worktreePath) {
        GitOutput listing = runAllowFailure(repoPath, gitCommand, "worktree", "list", "--porcelain");
        if (listing.exitCode() != 0) {
            return false;
        }
        String current = null;
        for (String line : listing.output().split("\\R")) {
            if (line.startsWith("worktree ")) {
                current = line.substring("worktree ".length()).trim();
            } else if ("locked".equals(line.trim()) && samePath(current, worktreePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePath(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            return new File(left).getCanonicalPath().equals(new File(right).getCanonicalPath());
        } catch (IOException e) {
            return left.equals(right);
        }
    }

    private record GitOutput(int exitCode, String output) {
    }

    private GitOutput runAllowFailure(String workDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(workDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitOutput(-1, "git command timed out");
            }
            return new GitOutput(process.exitValue(),
                    new String(process.getInputStream().readAllBytes()));
        } catch (IOException e) {
            return new GitOutput(-1, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitOutput(-1, "git command interrupted");
        }
    }

    private String runCommand(String workDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(workDir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git command timed out");
            }
            int exitCode = process.exitValue();
            String output = new String(process.getInputStream().readAllBytes());
            if (exitCode != 0) {
                throw new IllegalStateException("git command failed: " + String.join(" ", command) + " -> " + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run git command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git command interrupted", e);
        }
    }
}
