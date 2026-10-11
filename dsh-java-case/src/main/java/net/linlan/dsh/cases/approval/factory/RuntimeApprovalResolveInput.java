package net.linlan.dsh.cases.approval.factory;

import net.linlan.dsh.domain.agent.model.valobj.RuntimeApprovalDecisionVO;

/**
 * 运行期审批裁决 Pipeline 输入。
 * <p>流程：CaseImpl 归一化请求 → 创建本输入 → ResolveRuntimeApprovalNode 消费。</p>
 * <p>数据示例：{@code approvalId=approval-001, decision=ALLOW_ONCE}。</p>
 */
public record RuntimeApprovalResolveInput(String approvalId, RuntimeApprovalDecisionVO decision) {
}
