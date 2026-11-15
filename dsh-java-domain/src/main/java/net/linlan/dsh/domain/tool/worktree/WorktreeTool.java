package net.linlan.dsh.domain.tool.worktree;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import net.linlan.dsh.domain.tool.worktree.model.valobj.WorktreeCreatedPayload;

import java.time.Instant;
import java.util.Objects;

/**
 * WorktreeTool 模型通过一次工具调用创建新的本地 Git 工作树并改变当前 Session 目录。
 * 实验性扩展，默认关闭。
 */
public class WorktreeTool {

    private final ISessionEventStore eventStore;

    public WorktreeTool() {
        this(null);
    }

    public WorktreeTool(ISessionEventStore eventStore) {
        this.eventStore = eventStore;
    }

    public WorktreeResult create(String repoPath, String branchName) {
        return create(repoPath, branchName, null);
    }

    /**
     * 创建工作树并在指定会话写入创建事件。
     *
     * @param repoPath   主仓库路径
     * @param branchName 分支名
     * @param sessionId  会话 ID，可为 null
     * @return 工作树结果
     */
    public WorktreeResult create(String repoPath, String branchName, String sessionId) {
        Objects.requireNonNull(repoPath, "repoPath 不能为空");
        Objects.requireNonNull(branchName, "branchName 不能为空");
        if (!isGitRepo(repoPath)) {
            throw new NotGitRepoException("非 Git 仓库: " + repoPath);
        }
        String worktreePath = repoPath + "-worktree-" + branchName;
        if (eventStore != null && sessionId != null && !sessionId.isBlank()) {
            eventStore.append(sessionId, new SessionEvent.Generic(
                    eventStore.getLastSeq(sessionId) + 1,
                    Instant.now(),
                    SessionEventType.WORKTREE_CREATED,
                    payloadJson(new WorktreeCreatedPayload(repoPath, worktreePath, branchName))));
        }
        return new WorktreeResult(worktreePath, branchName);
    }

    private boolean isGitRepo(String path) {
        return new java.io.File(path, ".git").exists();
    }

    private static String payloadJson(WorktreeCreatedPayload payload) {
        return "{\"repoPath\":\"" + escape(payload.repoPath())
                + "\",\"worktreePath\":\"" + escape(payload.worktreePath())
                + "\",\"branch\":\"" + escape(payload.branch()) + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
