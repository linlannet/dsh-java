package net.linlan.dsh.domain.tool.worktree;

/**
 * WorktreeArchivePolicy 定义归档时的清理策略。
 *
 * @param retainOnArchive 是否保留工作树（默认 false）
 */
public record WorktreeArchivePolicy(boolean retainOnArchive) {

    /**
     * 默认策略：归档时尝试清理工作树，保留分支与提交。
     *
     * @return 默认策略
     */
    public static WorktreeArchivePolicy defaults() {
        return new WorktreeArchivePolicy(false);
    }
}
