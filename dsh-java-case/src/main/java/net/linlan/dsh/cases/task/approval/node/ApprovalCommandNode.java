package net.linlan.dsh.cases.task.approval.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.api.dto.ApprovalActionResponseDTO;
import net.linlan.dsh.domain.task.IApprovalCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 任务审批通过节点。
 * <p>流程：校验 sessionId 格式 → 调用审批领域命令服务执行通过操作 → 转换为审批动作 DTO。</p>
 * <p>数据示例：传入 {@code sessionId=BLUE-8090}；
 * 返回 {@code status=APPROVED, message="task approved", operatedAt=2026-09-07T10:00:00Z}。</p>
 */
@Service
public class ApprovalCommandNode extends AbstractStrategyRouter<String, NoContext, ApprovalActionResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(ApprovalCommandNode.class);

    private final IApprovalCommandService approvalCommandService;

    public ApprovalCommandNode(IApprovalCommandService approvalCommandService) {
        this.approvalCommandService = approvalCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected ApprovalActionResponseDTO doApply(String sessionId, NoContext context) {
        CaseGuard.requireSessionId(sessionId);
        var result = approvalCommandService.approve(sessionId);
        log.info("[TaskApproval] 任务审批通过，sessionId={} status={}", result.sessionId(), result.status());
        return new ApprovalActionResponseDTO(
                result.sessionId(), result.status(), result.message(), result.operatedAt()
        );
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<String, NoContext, ApprovalActionResponseDTO> getNext(String sessionId, NoContext context) {
        return defaultStrategyHandler;
    }
}
