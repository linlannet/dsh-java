package net.linlan.dsh.cases.task.approval.factory;

import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.api.dto.ApprovalActionResponseDTO;
import net.linlan.dsh.api.dto.PendingApprovalResponseDTO;
import net.linlan.dsh.cases.task.approval.node.ApprovalCommandNode;
import net.linlan.dsh.cases.task.approval.node.PendingApprovalQueryNode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 任务审批 Pipeline 工厂。
 * <p>流程：按命令/查询目标组装两条无共享上下文的 Pipeline，分别路由到审批通过和待审批查询节点。</p>
 * <p>数据示例：审批传入 {@code sessionId=BLUE-8090} 返回 {@code status=APPROVED}；
 * 查询传入 {@code limit=50} 返回待审批列表。</p>
 */
@Service
public class TaskApprovalPipelineFactory
        implements CasePipelineFactory<String, NoContext, ApprovalActionResponseDTO> {

    private final ApprovalCommandNode approvalCommandNode;
    private final PendingApprovalQueryNode pendingApprovalQueryNode;

    public TaskApprovalPipelineFactory(
            ApprovalCommandNode approvalCommandNode,
            PendingApprovalQueryNode pendingApprovalQueryNode
    ) {
        this.approvalCommandNode = approvalCommandNode;
        this.pendingApprovalQueryNode = pendingApprovalQueryNode;
    }

    /**
     * 构建任务审批通过 Pipeline。
     */
    public CasePipeline<String, NoContext, ApprovalActionResponseDTO> approvePipeline() {
        return CasePipeline.of("Approve task", approvalCommandNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建待审批任务查询 Pipeline。
     */
    public CasePipeline<Integer, NoContext, List<PendingApprovalResponseDTO>> pendingApprovalsPipeline() {
        return CasePipeline.of("List pending approvals", pendingApprovalQueryNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建默认执行 Pipeline，绑定首节点和上下文。
     */
    @Override
    public CasePipeline<String, NoContext, ApprovalActionResponseDTO> pipeline() {
        return approvePipeline();
    }
}
