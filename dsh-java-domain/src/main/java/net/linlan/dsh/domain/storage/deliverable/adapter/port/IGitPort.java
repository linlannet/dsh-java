package net.linlan.dsh.domain.storage.deliverable.adapter.port;

public interface IGitPort {

    String createWorktree(String repoPath, String branch, String targetPath);

    String currentCommit(String repoPath);

    boolean isClean(String repoPath);
}
