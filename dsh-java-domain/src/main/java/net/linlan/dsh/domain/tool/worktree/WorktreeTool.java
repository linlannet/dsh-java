package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.common.error.HarnessError;

import java.util.Objects;

/**
 * WorktreeTool 模型通过一次工具调用创建新的本地 Git 工作树并改变当前 Session 目录。
 * 实验性扩展，默认关闭。
 */
public class WorktreeTool {

    public WorktreeResult create(String repoPath, String branchName) {
        Objects.requireNonNull(repoPath, "repoPath 不能为空");
        Objects.requireNonNull(branchName, "branchName 不能为空");
        if (!isGitRepo(repoPath)) {
            throw new NotGitRepoException("非 Git 仓库: " + repoPath);
        }
        String worktreePath = repoPath + "-worktree-" + branchName;
        return new WorktreeResult(worktreePath, branchName);
    }

    private boolean isGitRepo(String path) {
        return new java.io.File(path, ".git").exists();
    }

    public record WorktreeResult(String path, String branch) { }

    /**
     * NotGitRepoException 表示目标目录不是 Git 仓库。
     */
    public static class NotGitRepoException extends HarnessError {

        /** 非 Git 仓库错误码。 */
        public static final String NOT_GIT_REPO = "WORKTREE_NOT_GIT_REPO";

        public NotGitRepoException(String message) {
            super(NOT_GIT_REPO, message);
        }
    }
}
