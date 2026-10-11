package net.linlan.dsh.domain.task.approval.service;

import net.linlan.dsh.domain.task.IApprovalPolicyService;
import net.linlan.dsh.domain.task.approval.adapter.port.IApprovalPolicyPort;
import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalDecisionVO;
import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalPolicyVO;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionAssessmentVO;
import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import org.springframework.stereotype.Service;

/**
 * 审批决策服务。
 * <p>
 * 执行流程：
 * 1. 读取当前审批策略；
 * 2. 先判断执行画像是否强制审批；
 * 3. 再判断工具权限是否触发审批，最终返回决策结果。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class ApprovalPolicyService implements IApprovalPolicyService {

    private final IApprovalPolicyPort approvalPolicyPort;

    /**
     * 处理审批 策略 Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param approvalPolicyPort 审批 策略 Port参数。
     * @return 方法执行结果。
     */
    public ApprovalPolicyService(IApprovalPolicyPort approvalPolicyPort) {
        this.approvalPolicyPort = approvalPolicyPort;
    }

    /**
     * 生成审批决策。
     * <p>
     * 执行流程：
     * 1. 读取审批策略；
     * 2. 按画像和工具权限依次判断；
     * 3. 返回最终决策对象。
     */
    @Override
    public ApprovalDecisionVO decide(String profileCode, PermissionAssessmentVO permissionAssessment) {
        ApprovalPolicyVO approvalPolicy = approvalPolicyPort.load();
        if (approvalPolicy.approvalRequiredProfiles().contains(profileCode)) {
            return new ApprovalDecisionVO(
                    HarnessStatusEnumVO.PENDING_APPROVAL,
                    true,
                    "执行画像需要人工审批：" + profileCode
            );
        }
        if (permissionAssessment.approvalRequired()) {
            return new ApprovalDecisionVO(
                    HarnessStatusEnumVO.PENDING_APPROVAL,
                    true,
                    "工具需要人工审批：" + String.join(",", permissionAssessment.approvalRequiredTools())
            );
        }
        return new ApprovalDecisionVO(HarnessStatusEnumVO.QUEUED, false, "任务自动通过");
    }
}
