package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.task.permission.adapter.port.IPermissionMatrixPort;
import net.linlan.dsh.domain.task.permission.model.valobj.PermissionMatrixVO;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapts configured tool-permission rules into the permission domain.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Component
public class PermissionMatrixPort implements IPermissionMatrixPort {

    private final List<String> approvalRequiredTools;

    /**
     * 处理Permission Matrix Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param ("${harness.approval.required-tools:shell_execute harness 审批 required tools Shell execute参数。
     * @param fs_write fs write参数。
     * @param plugin.run 插件 run参数。
     * @param approvalRequiredTools 审批 Required Tools参数。
     * @return 方法执行结果。
     */
    public PermissionMatrixPort(@Value("${harness.approval.required-tools:shell_execute,fs_write,plugin.run,subprocess.spawn}") List<String> approvalRequiredTools) {
        this.approvalRequiredTools = List.copyOf(approvalRequiredTools);
    }
    /**
     * 加载。
     */

    @Override
    public PermissionMatrixVO load() {
        return new PermissionMatrixVO(Set.copyOf(approvalRequiredTools));
    }
}
