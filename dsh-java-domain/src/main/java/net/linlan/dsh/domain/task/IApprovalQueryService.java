package net.linlan.dsh.domain.task;

import net.linlan.dsh.domain.task.approval.model.valobj.PendingApprovalVO;
import java.util.List;

/**
 * IApprovalQueryService 封装IApprovalQuery的领域规则与流程。
 */
public interface IApprovalQueryService {

    List<PendingApprovalVO> listPendingApprovals(int limit);
}
