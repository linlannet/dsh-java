package net.linlan.dsh.cases.task;

import net.linlan.dsh.api.dto.PendingApprovalResponseDTO;

import net.linlan.dsh.cases.task.approval.factory.TaskApprovalPipelineFactory;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 待审批任务查询编排实现。
 * <p>流程：携带 limit 进入审批查询 Pipeline → 执行待审批查询节点 → 返回待审批 DTO 列表。</p>
 * <p>数据示例：{@code listPendingApprovals(50)} 返回最多 50 条
 * {@code status=PENDING} 的会话审批摘要。</p>
 */
@Service
public class PendingApprovalQueryCaseImpl implements IPendingApprovalQueryCase {
    private final TaskApprovalPipelineFactory taskApprovalPipelineFactory;

    public PendingApprovalQueryCaseImpl(TaskApprovalPipelineFactory taskApprovalPipelineFactory) {
        this.taskApprovalPipelineFactory = taskApprovalPipelineFactory;
    }

    /**
     * 编排待审批任务查询，将 limit 交给查询 Pipeline。
     */
    @Override
    public List<PendingApprovalResponseDTO> listPendingApprovals(int limit) {
        return taskApprovalPipelineFactory.pendingApprovalsPipeline().execute(limit);
    }
}
