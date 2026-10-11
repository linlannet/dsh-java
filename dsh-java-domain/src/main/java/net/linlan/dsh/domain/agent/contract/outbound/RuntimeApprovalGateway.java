package net.linlan.dsh.domain.agent.contract.outbound;

import net.linlan.dsh.domain.tool.service.RuntimeApprovalGate.ApprovalRequest;
import net.linlan.dsh.domain.tool.service.RuntimeApprovalGate.ApprovalVerdict;

import java.util.List;
import java.util.Map;

/**
 * 运行期工具审批 Broker 端口。
 * <p>
 * agent 执行到需审批的工具时通过本端口挂起等待人工裁决；触发层（{@code RuntimeApprovalController}）
 * 依赖本端口列出待决条目并提交裁决，基础设施层提供具体实现（{@code RuntimeApprovalBroker}），
 * 使触发层不直接耦合基础设施，遵循六边形架构依赖方向。
 *
 *
 */
public interface RuntimeApprovalGateway {

    /** 挂起一次工具调用，阻塞等待人工裁决。 */
    ApprovalVerdict requestApproval(ApprovalRequest request);

    /**
     * 提交裁决。
     *
     * @return true 表示裁决被接受；false 表示审批不存在或已裁决
     */
    boolean resolve(String approvalId, ApprovalVerdict verdict);

    /** 列出当前全部待决审批（供前端轮询）。 */
    List<PendingApprovalView> listPending();

    /** 待决数（测试与监控用）。 */
    int pendingCount();

    /** 待决审批的对外视图。 */
    record PendingApprovalView(
            String approvalId,
            String sessionId,
            String toolName,
            String displayCommand,
            Map<String, Object> arguments,
            String createdAt
    ) {}
}
