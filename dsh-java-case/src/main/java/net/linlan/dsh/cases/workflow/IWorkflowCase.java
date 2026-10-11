package net.linlan.dsh.cases.workflow;

import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;
import net.linlan.dsh.api.dto.WorkflowEventResponseDTO;
import net.linlan.dsh.api.dto.WorkflowStartResponseDTO;
import net.linlan.dsh.domain.workflow.model.valobj.WorkflowEventVO;
import java.util.function.Consumer;

/**
 * 工作流启动、流式启动和取消用例契约。
 * <p>流程：Trigger/API/SSE → 本接口 → WorkflowPipelineFactory → 工作流领域服务。</p>
 * <p>数据示例：启动传入 {@code script=deploy.sh, name=demo, args=["--env=test"]}；
 * 取消传入 {@code runId=run-001, reason=用户停止}。</p>
 */
public interface IWorkflowCase {

    WorkflowStartResponseDTO start(StartWorkflowRequestDTO request);

    WorkflowStartResponseDTO startStreaming(StartWorkflowRequestDTO request, Consumer<WorkflowEventResponseDTO> eventSink);

    void cancel(String runId, String reason);
}
