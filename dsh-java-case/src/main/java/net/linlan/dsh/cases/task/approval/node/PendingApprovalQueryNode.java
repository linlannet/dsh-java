package net.linlan.dsh.cases.task.approval.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.PendingApprovalResponseDTO;
import net.linlan.dsh.domain.task.IApprovalQueryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 待审批任务查询节点。
 * <p>流程：接收 limit → 调用审批领域查询服务 → 将待审批实体映射为 DTO 列表。</p>
 * <p>数据示例：传入 {@code limit=50}；
 * 返回 {@code [{sessionId: "BLUE-8090", title: "Delete files", preview: "rm -rf /tmp/demo", status: "PENDING"}]}。</p>
 */
@Service
public class PendingApprovalQueryNode
        extends AbstractStrategyRouter<Integer, NoContext, List<PendingApprovalResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(PendingApprovalQueryNode.class);

    private final IApprovalQueryService approvalQueryService;

    public PendingApprovalQueryNode(IApprovalQueryService approvalQueryService) {
        this.approvalQueryService = approvalQueryService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<PendingApprovalResponseDTO> doApply(Integer limit, NoContext context) {
        var result = approvalQueryService.listPendingApprovals(limit).stream()
                .map(item -> new PendingApprovalResponseDTO(
                        item.sessionId(), item.title(), item.preview(), item.status(), item.createdAt()
                ))
                .toList();
        log.debug("[TaskApproval] 待审批任务查询完成，limit={} count={}", limit, result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Integer, NoContext, List<PendingApprovalResponseDTO>> getNext(Integer limit, NoContext context) {
        return defaultStrategyHandler;
    }
}
