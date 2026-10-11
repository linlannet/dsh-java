package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.task.approval.adapter.port.IApprovalPolicyPort;
import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalPolicyVO;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapts configured approval-profile rules into the approval domain.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Component
public class ApprovalPolicyPort implements IApprovalPolicyPort {

    private final List<String> approvalRequiredProfiles;

    /**
     * 处理审批 策略 Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param approvalRequiredProfiles 审批 Required Profiles参数。
     * @return 方法执行结果。
     */
    public ApprovalPolicyPort(@Value("${harness.approval.required-profiles:web}") List<String> approvalRequiredProfiles) {
        this.approvalRequiredProfiles = List.copyOf(approvalRequiredProfiles);
    }
    /**
     * 加载。
     */

    @Override
    public ApprovalPolicyVO load() {
        return new ApprovalPolicyVO(Set.copyOf(approvalRequiredProfiles));
    }
}
