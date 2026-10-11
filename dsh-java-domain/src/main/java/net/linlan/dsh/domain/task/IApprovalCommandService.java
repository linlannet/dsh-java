package net.linlan.dsh.domain.task;

import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalActionVO;

/**
 * IApprovalCommandService 封装IApprovalCommand命令的领域规则与流程。
 */
public interface IApprovalCommandService {

    ApprovalActionVO approve(String sessionId);
}
