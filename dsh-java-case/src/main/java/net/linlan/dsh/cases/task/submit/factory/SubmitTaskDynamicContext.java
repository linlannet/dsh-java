package net.linlan.dsh.cases.task.submit.factory;

import net.linlan.dsh.domain.runtime.model.model.valobj.ModelProviderProfileVO;
import net.linlan.dsh.domain.runtime.profile.model.valobj.ExecutionProfileVO;
import net.linlan.dsh.domain.session.registry.model.valobj.SessionOpenedResultVO;
import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalDecisionVO;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionAssessmentVO;
import net.linlan.dsh.domain.task.queue.model.valobj.QueuedTaskResultVO;
import net.linlan.dsh.domain.task.submission.model.valobj.TaskSubmissionVO;
import java.util.List;

/**
 * 任务提交策略树中流转的可变上下文。
 * 每个节点读取已累积状态，并写入自己的处理结果。
 *
 * <p>调用流程：SubmissionRootNode → ProfileResolutionNode → ToolResolutionNode → PermissionCheckNode → EnqueueNode。</p>
 * <p>示例：提交一条任务后，先解析执行画像，再校验权限，最后进入待执行队列。</p>
 *
 */
public class SubmitTaskDynamicContext {

    private TaskSubmissionVO submission;
    private ExecutionProfileVO executionProfile;
    private ModelProviderProfileVO modelChannel;
    private List<String> enabledTools;
    private PermissionAssessmentVO permissionAssessment;
    private ApprovalDecisionVO approvalDecision;
    private SessionOpenedResultVO sessionResult;
    private QueuedTaskResultVO taskResult;
    private String currentStatus;

    /**
     * 获取Submission。
     */
    public TaskSubmissionVO getSubmission() { return submission; }
    /**
     * 设置Submission。
     */
    public void setSubmission(TaskSubmissionVO submission) { this.submission = submission; }

    /**
     * 获取Execution Profile。
     */
    public ExecutionProfileVO getExecutionProfile() { return executionProfile; }
    /**
     * 设置Execution Profile。
     */
    public void setExecutionProfile(ExecutionProfileVO executionProfile) { this.executionProfile = executionProfile; }

    /**
     * 获取Model Channel。
     */
    public ModelProviderProfileVO getModelChannel() { return modelChannel; }
    /**
     * 设置Model Channel。
     */
    public void setModelChannel(ModelProviderProfileVO modelChannel) { this.modelChannel = modelChannel; }

    /**
     * 获取Enabled Tools。
     */
    public List<String> getEnabledTools() { return enabledTools; }
    /**
     * 设置Enabled Tools。
     */
    public void setEnabledTools(List<String> enabledTools) { this.enabledTools = enabledTools; }

    /**
     * 获取Permission Assessment。
     */
    public PermissionAssessmentVO getPermissionAssessment() { return permissionAssessment; }
    /**
     * 设置Permission Assessment。
     */
    public void setPermissionAssessment(PermissionAssessmentVO permissionAssessment) { this.permissionAssessment = permissionAssessment; }

    /**
     * 获取Approval Decision。
     */
    public ApprovalDecisionVO getApprovalDecision() { return approvalDecision; }
    /**
     * 设置Approval Decision。
     */
    public void setApprovalDecision(ApprovalDecisionVO approvalDecision) { this.approvalDecision = approvalDecision; }

    /**
     * 获取Session Result。
     */
    public SessionOpenedResultVO getSessionResult() { return sessionResult; }
    /**
     * 设置Session Result。
     */
    public void setSessionResult(SessionOpenedResultVO sessionResult) { this.sessionResult = sessionResult; }

    /**
     * 获取Task Result。
     */
    public QueuedTaskResultVO getTaskResult() { return taskResult; }
    /**
     * 设置Task Result。
     */
    public void setTaskResult(QueuedTaskResultVO taskResult) { this.taskResult = taskResult; }

    /**
     * 获取Current Status。
     */
    public String getCurrentStatus() { return currentStatus; }
    /**
     * 设置Current Status。
     */
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
}
