package net.linlan.dsh.domain.session.log.service;

import net.linlan.dsh.domain.session.ISessionLogService;
import net.linlan.dsh.domain.session.log.adapter.repository.ISessionEventRepository;
import net.linlan.dsh.domain.session.log.model.entity.SessionEventEntity;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Owns append-only session event recording.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
@Service
public class SessionLogService implements ISessionLogService {

    private final ISessionEventRepository sessionEventRepository;

    /**
     * 处理会话 Log Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionEventRepository 会话 事件 Repository参数。
     * @return 方法执行结果。
     */
    public SessionLogService(ISessionEventRepository sessionEventRepository) {
        this.sessionEventRepository = sessionEventRepository;
    }

    @Override
    public void recordTaskSubmission(String sessionId, String taskId, String profileCode, String prompt, boolean approvalRequired, String approvalReason) {
        Instant eventTime = Instant.now();
        sessionEventRepository.append(new SessionEventEntity(sessionId, "task.submitted", "taskId=" + taskId + ",profile=" + profileCode, eventTime));
        sessionEventRepository.append(new SessionEventEntity(sessionId, "user.message", prompt, eventTime));
        if (approvalRequired) {
            sessionEventRepository.append(new SessionEventEntity(sessionId, "assistant.message", "任务已提交，等待审批。" + approvalReason, eventTime));
            sessionEventRepository.append(new SessionEventEntity(sessionId, "task.approval.required", approvalReason, eventTime));
            return;
        }
        sessionEventRepository.append(new SessionEventEntity(sessionId, "task.auto.approved", approvalReason, eventTime));
    }
    /**
     * record Approval Accepted。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     */


    @Override
    public void recordApprovalAccepted(String sessionId) {
        Instant eventTime = Instant.now();
        sessionEventRepository.append(new SessionEventEntity(sessionId, "task.approved", "approved from web console", eventTime));
    }
    /**
     * record Execution Started。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param resolvedModel resolved Model参数。
     */


    @Override
    public void recordExecutionStarted(String sessionId, String resolvedModel) {
        Instant eventTime = Instant.now();
        sessionEventRepository.append(new SessionEventEntity(sessionId, "task.execution.started", "model=" + resolvedModel, eventTime));
    }
    /**
     * record Execution Completed。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param modelCode model Code参数。
     * @param assistantMessage assistant Message参数。
     */


    @Override
    public void recordExecutionCompleted(String sessionId, String modelCode, String assistantMessage) {
        Instant eventTime = Instant.now();
        sessionEventRepository.append(new SessionEventEntity(sessionId, "assistant.message", assistantMessage, eventTime));
        sessionEventRepository.append(new SessionEventEntity(sessionId, "task.execution.completed", "model=" + modelCode, eventTime));
    }
    /**
     * record Execution Failed。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param failureMessage failure Message参数。
     */


    @Override
    public void recordExecutionFailed(String sessionId, String failureMessage) {
        Instant eventTime = Instant.now();
        sessionEventRepository.append(new SessionEventEntity(sessionId, "assistant.message", failureMessage, eventTime));
        sessionEventRepository.append(new SessionEventEntity(sessionId, "task.execution.failed", failureMessage, eventTime));
    }
}
