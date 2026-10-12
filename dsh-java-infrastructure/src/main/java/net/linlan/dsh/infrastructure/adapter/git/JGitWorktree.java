package net.linlan.dsh.infrastructure.adapter.git;

import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class JGitWorktree implements IGitPort {

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
