package net.linlan.dsh.domain.task;

import net.linlan.dsh.domain.task.permission.model.valobj.PermissionAssessmentVO;
import java.util.List;

/**
 * 权限评估服务接口。
 * <p>
 * 执行流程：
 * 1. 上层传入已启用工具集合；
 * 2. 领域层结合权限矩阵进行评估；
 * 3. 返回工具分组和是否需要审批的结果。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IPermissionPolicyService {

    /**
     * 评估启用工具的权限状态。
     * <p>
     * 执行流程：
     * 1. 读取 enabledTools；
     * 2. 对照权限矩阵进行拆分；
     * 3. 返回权限评估结果。
     *
     * @param enabledTools 已启用的工具编码
     * @return 权限评估结果
     */
    PermissionAssessmentVO assess(List<String> enabledTools);
}
