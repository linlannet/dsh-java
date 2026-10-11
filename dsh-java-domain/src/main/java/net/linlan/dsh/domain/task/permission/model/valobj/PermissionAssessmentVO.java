package net.linlan.dsh.domain.task.permission.model.valobj;

import java.util.List;

/**
 * 权限评估结果值对象。
 * <p>
 * 执行流程：
 * 1. 工具解析后得到可启用工具；
 * 2. 权限策略将其拆分为需审批和可自动通过两类；
 * 3. 返回给审批策略服务继续决策。
 *
 * @param enabledTools 最终可启用的工具编码
 * @param approvalRequiredTools 需要人工审批的工具编码
 * @param autoApprovedTools 可自动通过的工具编码
 * @param approvalRequired 是否需要人工审批
 *
 *
 */
public record PermissionAssessmentVO(
        List<String> enabledTools,
        List<String> approvalRequiredTools,
        List<String> autoApprovedTools,
        boolean approvalRequired
) {
}
