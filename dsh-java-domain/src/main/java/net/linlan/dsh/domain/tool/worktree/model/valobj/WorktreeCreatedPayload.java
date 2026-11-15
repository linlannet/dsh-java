package net.linlan.dsh.domain.tool.worktree.model.valobj;

/**
 * WorktreeCreatedPayload 记录工作树创建事件载荷。
 *
 * @param repoPath     主仓库路径
 * @param worktreePath 工作树路径
 * @param branch       分支名
 */
public record WorktreeCreatedPayload(String repoPath, String worktreePath, String branch) {
}
