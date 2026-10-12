package net.linlan.dsh.domain.storage.deliverable;

import net.linlan.dsh.domain.storage.deliverable.adapter.port.IGitPort;

import java.nio.file.Path;
import java.util.Objects;

public final class GitWorktreeSnapshot {

    public record SnapshotResult(String snapshotPath, String branch, String commit) {}

    private final IGitPort gitPort;

    public GitWorktreeSnapshot(IGitPort gitPort) {
        Objects.requireNonNull(gitPort, "gitPort");
        this.gitPort = gitPort;
    }

    public String snapshot(String repoPath, String branch) {
        return snapshotWithPath(repoPath, branch).snapshotPath();
    }

    public SnapshotResult snapshotWithPath(String repoPath, String branch) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repoPath required");
        }
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("branch required");
        }
        String snapshotPath = Path.of(repoPath, ".snapshots", branch).toString();
        String createdPath = gitPort.createWorktree(repoPath, branch, snapshotPath);
        String commit = gitPort.currentCommit(repoPath);
        return new SnapshotResult(createdPath, branch, commit);
    }
}
