package net.linlan.dsh.domain.tool.worktree.adapter.port;

/**
 * IWorktreeCleanupPort 定义 Worktree 归档清理所需的状态查询与移除能力。
 *
 * <p>调用流程：归档策略判定 → 查询工作树状态 → 非强制移除。</p>
 * <p>示例：脏树状态返回 {@code clean=false}，清理器跳过移除。</p>
 */
public interface IWorktreeCleanupPort {

    /**
     * 工作树状态。
     *
     * @param exists        路径是否存在
     * @param clean         是否无未提交变更
     * @param submodules    是否包含子模块
     * @param locked        是否被 git 锁定
     * @param detached      是否处于分离 HEAD
     * @param branch        当前分支，分离 HEAD 时为 null
     */
    record WorktreeState(boolean exists, boolean clean, boolean submodules,
                         boolean locked, boolean detached, String branch) {
    }

    /**
     * 移除结果。
     *
     * @param removed 是否移除成功
     * @param message 失败提示
     */
    record RemovalResult(boolean removed, String message) {
    }

    /**
     * 查询工作树状态。
     *
     * @param repoPath     主仓库路径
     * @param worktreePath 工作树路径
     * @return 工作树状态
     */
    WorktreeState inspect(String repoPath, String worktreePath);

    /**
     * 非强制移除工作树，保留分支与提交。
     *
     * @param repoPath     主仓库路径
     * @param worktreePath 工作树路径
     * @return 移除结果
     */
    RemovalResult removeWorktree(String repoPath, String worktreePath);
}
