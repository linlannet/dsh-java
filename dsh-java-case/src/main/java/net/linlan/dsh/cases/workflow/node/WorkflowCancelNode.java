package net.linlan.dsh.cases.workflow.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.cases.workflow.WorkflowCaseImpl;
import net.linlan.dsh.domain.workflow.IWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工作流取消节点。
 * <p>流程：校验 runId 非空 → 将 runId 和取消原因交给工作流领域服务 → Pipeline 终止。</p>
 * <p>数据示例：传入 {@code runId=run-001, reason=用户停止}；该运行被标记取消且无返回值。</p>
 */
@Service
public class WorkflowCancelNode
        extends AbstractStrategyRouter<WorkflowCaseImpl.WorkflowCancelInput, NoContext, Void> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCancelNode.class);

    private final IWorkflowService workflowService;

    public WorkflowCancelNode(IWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected Void doApply(WorkflowCaseImpl.WorkflowCancelInput input, NoContext context) {
        CaseGuard.requireNonNull(input, "workflow cancel input must not be null");
        CaseGuard.requireText(input.runId(), "runId must not be blank");
        workflowService.cancel(input.runId(), input.reason());
        log.info("[Workflow] 工作流取消完成，runId={}", input.runId());
        return null;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<WorkflowCaseImpl.WorkflowCancelInput, NoContext, Void> getNext(
            WorkflowCaseImpl.WorkflowCancelInput input, NoContext context
    ) {
        return defaultStrategyHandler;
    }
}
