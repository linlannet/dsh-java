package net.linlan.dsh.cases.workflow.factory;

import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.workflow.WorkflowCaseImpl;
import net.linlan.dsh.cases.workflow.node.WorkflowCancelNode;
import net.linlan.dsh.cases.workflow.node.WorkflowStartNode;
import org.springframework.stereotype.Service;

/**
 * 工作流 Pipeline 工厂。
 * <p>流程：启动请求进入 Start Pipeline，取消请求进入 Cancel Pipeline，均使用无共享上下文的单节点编排。</p>
 * <p>数据示例：启动 {@code script=deploy.sh} 返回 {@code runId=run-001, status=STARTED}；
 * 取消 {@code runId=run-001} 无返回值。</p>
 */
@Service
public class WorkflowPipelineFactory
        implements CasePipelineFactory<WorkflowStartNode.Input, NoContext, WorkflowStartResponseDTO> {

    private final WorkflowStartNode workflowStartNode;
    private final WorkflowCancelNode workflowCancelNode;

    public WorkflowPipelineFactory(
            WorkflowStartNode workflowStartNode,
            WorkflowCancelNode workflowCancelNode
    ) {
        this.workflowStartNode = workflowStartNode;
        this.workflowCancelNode = workflowCancelNode;
    }

    /**
     * 构建工作流启动 Pipeline。
     */
    public CasePipeline<WorkflowStartNode.Input, NoContext, WorkflowStartResponseDTO> startPipeline() {
        return CasePipeline.of("Start workflow", workflowStartNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建工作流取消 Pipeline。
     */
    public CasePipeline<WorkflowCaseImpl.WorkflowCancelInput, NoContext, Void> cancelPipeline() {
        return CasePipeline.of("Cancel workflow", workflowCancelNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建默认执行 Pipeline，绑定首节点和上下文。
     */
    @Override
    public CasePipeline<WorkflowStartNode.Input, NoContext, WorkflowStartResponseDTO> pipeline() {
        return startPipeline();
    }
}
