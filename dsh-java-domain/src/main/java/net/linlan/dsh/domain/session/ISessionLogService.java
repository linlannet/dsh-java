package net.linlan.dsh.domain.session;

/**
 * Owns append-only session event recording.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public interface ISessionLogService {

    void recordTaskSubmission(String sessionId, String taskId, String profileCode, String prompt, boolean approvalRequired, String approvalReason);

    void recordApprovalAccepted(String sessionId);

    void recordExecutionStarted(String sessionId, String resolvedModel);

    void recordExecutionCompleted(String sessionId, String modelCode, String assistantMessage);

    void recordExecutionFailed(String sessionId, String failureMessage);
}
