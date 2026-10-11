package net.linlan.dsh.cases.approval.node;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.cases.approval.factory.RuntimeApprovalResolveInput;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.agent.contract.outbound.RuntimeApprovalGateway;
import net.linlan.dsh.domain.agent.model.valobj.RuntimeApprovalDecisionVO;
import net.linlan.dsh.domain.tool.service.RuntimeApprovalGate.ApprovalVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 对运行期工具审批执行用户裁决并唤醒挂起的 Agent。
 * <p>流程：校验输入 → DTO 决策转 ApprovalVerdict → Broker.resolve → 返回结果。</p>
 * <p>数据示例：{@code approvalId=approval-001, decision=ALLOW_ONCE} 成功后返回
 * {@code resolved=true}；不存在或已裁决时返回 {@code resolved=false}。</p>
 */
@Service
public class ResolveRuntimeApprovalNode
        extends AbstractStrategyRouter<RuntimeApprovalResolveInput, NoContext, ResolveRuntimeApprovalResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(ResolveRuntimeApprovalNode.class);

    private final RuntimeApprovalGateway approvalBroker;

    public ResolveRuntimeApprovalNode(RuntimeApprovalGateway approvalBroker) {
        this.approvalBroker = approvalBroker;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected ResolveRuntimeApprovalResponseDTO doApply(
            RuntimeApprovalResolveInput input, NoContext context
    ) {
        CaseGuard.requireNonNull(input, "approval input must not be null");
        CaseGuard.requireText(input.approvalId(), "approvalId must not be blank");
        boolean resolved = approvalBroker.resolve(input.approvalId(), toVerdict(input.decision()));
        log.info("[Approval] 运行期审批裁决完成，approvalId={} decision={} resolved={}",
                input.approvalId(), input.decision(), resolved);
        return resolved
                ? new ResolveRuntimeApprovalResponseDTO(true, input.decision().name(), null)
                : new ResolveRuntimeApprovalResponseDTO(false, null, "approval not found or already resolved");
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<RuntimeApprovalResolveInput, NoContext, ResolveRuntimeApprovalResponseDTO> getNext(
            RuntimeApprovalResolveInput input, NoContext context
    ) {
        return defaultStrategyHandler;
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private ApprovalVerdict toVerdict(RuntimeApprovalDecisionVO decision) {
        return switch (decision) {
            case ALLOW_ONCE -> ApprovalVerdict.ALLOW_ONCE;
            case ALLOW_SESSION -> ApprovalVerdict.ALLOW_SESSION;
            case DENY -> ApprovalVerdict.DENY;
            case CANCEL -> ApprovalVerdict.CANCEL;
        };
    }
}
