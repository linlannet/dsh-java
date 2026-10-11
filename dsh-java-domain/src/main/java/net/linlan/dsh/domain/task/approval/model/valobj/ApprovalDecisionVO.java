package net.linlan.dsh.domain.task.approval.model.valobj;

import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;

/**
 * ApprovalDecisionVO 表示ApprovalDecision的领域值对象。
 */
public record ApprovalDecisionVO(
        HarnessStatusEnumVO status,
        boolean approvalRequired,
        String reason
) {
}
