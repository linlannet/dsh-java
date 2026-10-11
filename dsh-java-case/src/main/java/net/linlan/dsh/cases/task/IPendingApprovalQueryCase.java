package net.linlan.dsh.cases.task;

import net.linlan.dsh.api.dto.PendingApprovalResponseDTO;

import java.util.List;

/**
 * 待审批任务查询用例契约。
 * <p>流程：Trigger/前端 → 本接口 → 审批查询 Pipeline → 任务审批领域查询服务。</p>
 * <p>数据示例：传入 {@code limit=50}；返回 {@code sessionId=BLUE-8090, title=Delete files, status=PENDING}。</p>
 */
public interface IPendingApprovalQueryCase {
    List<PendingApprovalResponseDTO> listPendingApprovals(int limit);
}
