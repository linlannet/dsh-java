package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.tool.worktree.adapter.port.IWorktreeCleanupPort;

import java.util.Objects;

/**
 * WorktreeArchiveCleaner 在归档时按安全条件清理 Worktree。
 *
 * <p>调用流程：retainOnArchive 判定 → 状态检查（脏树/子模块/锁/分离 HEAD）→ 非强制移除。</p>
 * <p>示例：脏树时返回 skipped，且不删除分支与提交。</p>
 */
public final class WorktreeArchiveCleaner {

    private final IWorktreeCleanupPort port;

    public WorktreeArchiveCleaner(IWorktreeCleanupPort port) {
        this.port = Objects.requireNonNull(port, "port must not be null");
    }

    /**
     * 归档清理。
     *
     * @param repoPath     主仓库路径
     * @param worktreePath 工作树路径
     * @param policy       归档策略，null 时使用默认策略
     * @return 清理结果
     */
    public ArchiveResult archive(String repoPath, String worktreePath, WorktreeArchivePolicy policy) {
        WorktreeArchivePolicy effective = policy == null ? WorktreeArchivePolicy.defaults() : policy;
        if (effective.retainOnArchive()) {
            return ArchiveResult.retained("retainOnArchive=true");
        }

        IWorktreeCleanupPort.WorktreeState state = port.inspect(repoPath, worktreePath);
        if (!state.exists()) {
            return ArchiveResult.retained("worktree path not found");
        }
        if (!state.clean()) {
            return ArchiveResult.retained("worktree has uncommitted changes");
        }
        if (state.submodules()) {
            return ArchiveResult.retained("worktree contains submodules");
        }
        if (state.locked()) {
            return ArchiveResult.retained("worktree is locked");
        }
        if (state.detached()) {
            return ArchiveResult.retained("worktree is in detached HEAD");
        }

        IWorktreeCleanupPort.RemovalResult result = port.removeWorktree(repoPath, worktreePath);
        if (result.removed()) {
            return ArchiveResult.removed(state.branch());
        }
        return ArchiveResult.failed(result.message());
    }

    /**
     * 归档结果。
     *
     * @param outcome 结果类型：removed/skipped/failed
     * @param message 说明或保留原因
     */
    public record ArchiveResult(String outcome, String message) {

        static ArchiveResult removed(String branch) {
            return new ArchiveResult("removed", "worktree removed; branch retained: " + branch);
        }

        static ArchiveResult retained(String reason) {
            return new ArchiveResult("skipped", reason);
        }

        static ArchiveResult failed(String reason) {
            return new ArchiveResult("failed", reason);
        }
    }
}
