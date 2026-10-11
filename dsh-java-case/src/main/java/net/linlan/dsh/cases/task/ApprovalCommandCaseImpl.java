package net.linlan.dsh.cases.task;

import net.linlan.dsh.api.dto.ApprovalActionResponseDTO;

import net.linlan.dsh.cases.task.approval.factory.TaskApprovalPipelineFactory;
import org.springframework.stereotype.Service;

/**
 * 任务审批通过编排实现。
 * <p>流程：携带 sessionId 进入审批 Pipeline → 执行审批命令节点 → 返回审批动作 DTO。</p>
 * <p>数据示例：{@code approve("BLUE-8090")} 后返回 {@code status=APPROVED} 和操作时间。</p>
 */
@Service
public class ApprovalCommandCaseImpl implements IApprovalCommandCase {
    private final TaskApprovalPipelineFactory taskApprovalPipelineFactory;

    public ApprovalCommandCaseImpl(TaskApprovalPipelineFactory taskApprovalPipelineFactory) {
        this.taskApprovalPipelineFactory = taskApprovalPipelineFactory;
    }

    /**
     * 编排任务审批通过，将 sessionId 交给审批 Pipeline。
     */
    @Override
    public ApprovalActionResponseDTO approve(String sessionId) {
        return taskApprovalPipelineFactory.approvePipeline().execute(sessionId);
    }
}
