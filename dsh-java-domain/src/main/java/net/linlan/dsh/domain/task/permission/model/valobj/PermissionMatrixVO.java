package net.linlan.dsh.domain.task.permission.model.valobj;

import java.util.Set;

/**
 * 权限矩阵值对象。
 * <p>
 * 执行流程：
 * 1. 基础设施层读取权限配置；
 * 2. 领域层根据矩阵判断工具是否需要审批；
 * 3. 返回给权限策略服务。
 *
 * @param approvalRequiredTools 需要人工审批的工具编码
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public record PermissionMatrixVO(Set<String> approvalRequiredTools) {

    public PermissionMatrixVO {
        approvalRequiredTools = approvalRequiredTools == null ? Set.of() : Set.copyOf(approvalRequiredTools);
    }
}
