package net.linlan.dsh.domain.task;

import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalDecisionVO;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionAssessmentVO;

/**
 * 审批决策服务接口。
 * <p>
 * 执行流程：
 * 1. 输入画像编码与权限评估结果；
 * 2. 结合审批策略判断是否进入人工审批；
 * 3. 返回最终审批决策。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IApprovalPolicyService {

    /**
     * 决定任务是否立即进入队列。
     * <p>
     * 执行流程：
     * 1. 读取 profileCode 和权限评估结果；
     * 2. 结合审批策略判断；
     * 3. 返回审批决策对象。
     *
     * @param profileCode 执行画像编码
     * @param permissionAssessment 权限评估结果
     * @return 审批决策
     */
    ApprovalDecisionVO decide(String profileCode, PermissionAssessmentVO permissionAssessment);
}
