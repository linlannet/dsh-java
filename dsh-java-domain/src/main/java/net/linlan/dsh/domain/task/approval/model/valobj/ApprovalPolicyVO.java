package net.linlan.dsh.domain.task.approval.model.valobj;

import java.util.Set;

/**
 * 审批策略值对象。
 * <p>
 * 执行流程：
 * 1. 基础设施层读取审批配置；
 * 2. 领域层按执行画像判断是否强制审批；
 * 3. 返回给审批决策服务。
 *
 * @param approvalRequiredProfiles 需要人工审批的执行画像编码
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public record ApprovalPolicyVO(Set<String> approvalRequiredProfiles) {

    public ApprovalPolicyVO {
        approvalRequiredProfiles = approvalRequiredProfiles == null ? Set.of() : Set.copyOf(approvalRequiredProfiles);
    }
}
