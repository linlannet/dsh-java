package net.linlan.dsh.cases.workflow.node;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import net.linlan.dsh.api.dto.WorkflowEventResponseDTO;
import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.workflow.IWorkflowService;
import net.linlan.dsh.domain.workflow.model.entity.WorkflowRun;
import net.linlan.dsh.domain.workflow.service.WorkflowEventPolicy;
import net.linlan.dsh.domain.workflow.service.WorkflowStartPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工作流启动节点。
 * <p>流程：校验启动请求 → 使用领域启动策略创建 WorkflowRun → 如有事件接收器则在完成时发布事件 → 返回启动结果。</p>
 * <p>数据示例：传入 {@code script=deploy.sh, name=demo, args=["--env=test"]}；
 * 返回 {@code runId=run-001, status=STARTED}，完成后可收到 {@code status=SUCCEEDED}。</p>
 */
@Service
public class WorkflowStartNode
        extends AbstractStrategyRouter<WorkflowStartNode.Input, NoContext, WorkflowStartResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStartNode.class);

    private final IWorkflowService workflowService;

    public WorkflowStartNode(IWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected WorkflowStartResponseDTO doApply(Input input, NoContext context) {
        CaseGuard.requireNonNull(input.request(), "workflow request must not be null");
        WorkflowRun run = workflowService.start(WorkflowStartPolicy.adhoc(
                input.request().script(),
                input.request().name(),
                input.request().args(),
                input.request().parentCwd()
        ));
        log.info("[Workflow] 工作流启动完成，runId={} name={}", run.id(), input.request().name());
        if (input.eventSink() != null) {
            run.result().whenComplete((result, error) -> {
                var policy = WorkflowEventPolicy.completed(run.id(), result, error);
                input.eventSink().accept(new WorkflowEventResponseDTO(
                        policy.runId(),
                        policy.kind().name().toLowerCase(),
                        policy.status(),
                        policy.output(),
                        policy.error()
                ));
                log.debug("[Workflow] 工作流完成事件已发送，runId={} status={} errorPresent={}",
                        run.id(), policy.status(), error != null);
            });
        }
        return new WorkflowStartResponseDTO(run.id(), "STARTED");
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Input, NoContext, WorkflowStartResponseDTO> getNext(Input input, NoContext context) {
        return defaultStrategyHandler;
    }

    public record Input(
            StartWorkflowRequestDTO request,
            java.util.function.Consumer<WorkflowEventResponseDTO> eventSink
    ) {
    }
}
