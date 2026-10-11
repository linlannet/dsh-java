package net.linlan.dsh.domain.workflow.adapter.port;

import net.linlan.dsh.domain.workflow.model.entity.WorkflowRun;
import net.linlan.dsh.domain.workflow.model.entity.WorkflowStartRequest;

/**
 * Port for executing workflow orchestration scripts.
 *
 * <p>调用流程：定义工作流 → 启动 → 节点推进 → 事件/状态回写。</p>
 * <p>示例：工作流按节点顺序执行，每个节点可写回下一步上下文。</p>
 *
 */
public interface IWorkflowEnginePort {

    /**
     * 启动生命周期或异步任务。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param request 请求。
     * @return 领域处理结果。
     */
    WorkflowRun start(WorkflowStartRequest request);
}
