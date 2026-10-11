package net.linlan.dsh.cases.task;

import net.linlan.dsh.api.dto.ApprovalActionResponseDTO;

/**
 * 任务审批通过用例契约。
 * <p>流程：Trigger/前端 → 本接口 → 审批 Pipeline → 任务审批领域命令服务。</p>
 * <p>数据示例：传入 {@code sessionId=BLUE-8090}；返回 {@code status=APPROVED, message="task approved"}。</p>
 */
public interface IApprovalCommandCase {
    ApprovalActionResponseDTO approve(String sessionId);
}
