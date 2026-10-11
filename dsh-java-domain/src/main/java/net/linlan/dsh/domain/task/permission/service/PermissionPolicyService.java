package net.linlan.dsh.domain.task.permission.service;

import net.linlan.dsh.domain.task.IPermissionPolicyService;
import net.linlan.dsh.domain.task.permission.adapter.port.IPermissionMatrixPort;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionAssessmentVO;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionMatrixVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 权限评估服务。
 * <p>
 * 执行流程：
 * 1. 读取权限矩阵；
 * 2. 将启用工具拆分为需审批和可自动通过两类；
 * 3. 返回权限评估结果。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class PermissionPolicyService implements IPermissionPolicyService {

    private final IPermissionMatrixPort permissionMatrixPort;

    /**
     * 处理Permission 策略 Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param permissionMatrixPort permission Matrix Port参数。
     * @return 方法执行结果。
     */
    public PermissionPolicyService(IPermissionMatrixPort permissionMatrixPort) {
        this.permissionMatrixPort = permissionMatrixPort;
    }

    /**
     * 评估启用工具权限。
     * <p>
     * 执行流程：
     * 1. 读取权限矩阵；
     * 2. 过滤出需要审批的工具；
     * 3. 组装评估结果返回。
     */
    @Override
    public PermissionAssessmentVO assess(List<String> enabledTools) {
        PermissionMatrixVO permissionMatrix = permissionMatrixPort.load();
        List<String> gatedTools = enabledTools.stream()
                .filter(permissionMatrix.approvalRequiredTools()::contains)
                .toList();
        List<String> autoApprovedTools = enabledTools.stream()
                .filter(toolCode -> !permissionMatrix.approvalRequiredTools().contains(toolCode))
                .toList();
        return new PermissionAssessmentVO(enabledTools, gatedTools, autoApprovedTools, !gatedTools.isEmpty());
    }
}
