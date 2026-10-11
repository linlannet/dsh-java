package net.linlan.dsh.cases.approval.node;

import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.domain.agent.contract.outbound.RuntimeApprovalGateway;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 查询当前所有等待用户裁决的运行期工具审批。
 * <p>流程：Pipeline 入口 → RuntimeApprovalBroker.listPending → 转换 RuntimeApprovalResponseDTO。</p>
 * <p>数据示例：返回 {@code approvalId=approval-001, toolName=shell_execute,
 * displayCommand=ls -la} 列表。</p>
 */
@Service
public class ListPendingApprovalsNode
        extends AbstractStrategyRouter<Void, NoContext, List<RuntimeApprovalResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(ListPendingApprovalsNode.class);

    private final RuntimeApprovalGateway approvalBroker;

    public ListPendingApprovalsNode(RuntimeApprovalGateway approvalBroker) {
        this.approvalBroker = approvalBroker;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<RuntimeApprovalResponseDTO> doApply(Void request, NoContext context) {
        var result = approvalBroker.listPending().stream()
                .map(item -> new RuntimeApprovalResponseDTO(
                        item.approvalId(),
                        item.sessionId(),
                        item.toolName(),
                        item.displayCommand(),
                        item.arguments(),
                        item.createdAt()
                ))
                .toList();
        log.debug("[Approval] 运行期待审批查询完成，count={}", result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Void, NoContext, List<RuntimeApprovalResponseDTO>> getNext(Void request, NoContext context) {
        return defaultStrategyHandler;
    }
}
