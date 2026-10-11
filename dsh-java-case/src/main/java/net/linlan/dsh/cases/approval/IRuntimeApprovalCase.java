package net.linlan.dsh.cases.approval;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalRequestDTO;
import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import java.util.List;

/**
 * 运行期工具审批用例契约。
 * <p>流程：Trigger/前端 → RuntimeApprovalCase → Pipeline → RuntimeApprovalBroker → Agent。</p>
 * <p>数据示例：先返回待审批项 {@code approvalId=approval-001, toolName=shell_execute}，
 * 再用 {@code verdict=ALLOW_ONCE} 裁决并让挂起的 Agent 继续执行。</p>
 */
public interface IRuntimeApprovalCase {

    List<RuntimeApprovalResponseDTO> listPending();

    ResolveRuntimeApprovalResponseDTO resolve(String approvalId, ResolveRuntimeApprovalRequestDTO request);
}
