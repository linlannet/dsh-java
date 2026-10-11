package net.linlan.dsh.cases.workflow;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import net.linlan.dsh.api.dto.WorkflowEventResponseDTO;
import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.cases.workflow.factory.WorkflowPipelineFactory;
import net.linlan.dsh.cases.workflow.node.WorkflowStartNode;
import net.linlan.dsh.domain.workflow.model.valobj.WorkflowEventVO;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * 工作流启动与取消编排实现。
 * <p>流程：把请求和可选事件接收器组装为启动输入 → 执行启动 Pipeline；取消时执行取消 Pipeline。</p>
 * <p>数据示例：{@code start(script="deploy.sh")} 返回 {@code runId=run-001, status=STARTED}；
 * 流式完成后事件接收器收到 {@code kind=completed, status=SUCCEEDED}。</p>
 */
@Service
public class WorkflowCaseImpl implements IWorkflowCase {

    private final WorkflowPipelineFactory workflowPipelineFactory;

    public WorkflowCaseImpl(WorkflowPipelineFactory workflowPipelineFactory) {
        this.workflowPipelineFactory = workflowPipelineFactory;
    }

    /**
     * 编排工作流启动，可选事件接收器用于完成回调。
     */
    @Override
    public WorkflowStartResponseDTO start(StartWorkflowRequestDTO request) {
        return workflowPipelineFactory.startPipeline()
                .execute(new WorkflowStartNode.Input(request, null));
    }

    /**
     * 编排流式工作流启动，启动后通过事件接收器回调结果。
     */
    @Override
    public WorkflowStartResponseDTO startStreaming(
            StartWorkflowRequestDTO request,
            Consumer<WorkflowEventResponseDTO> eventSink
    ) {
        return workflowPipelineFactory.startPipeline()
                .execute(new WorkflowStartNode.Input(request, eventSink));
    }

    /**
     * 编排工作流取消，将 runId 和原因交给取消 Pipeline。
     */
    @Override
    public void cancel(String runId, String reason) {
        workflowPipelineFactory.cancelPipeline().execute(new WorkflowCancelInput(runId, reason));
    }

    public record WorkflowCancelInput(String runId, String reason) {
    }
}
