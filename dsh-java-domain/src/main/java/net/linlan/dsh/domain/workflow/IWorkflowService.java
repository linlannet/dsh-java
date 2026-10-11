package net.linlan.dsh.domain.workflow;

import net.linlan.dsh.domain.workflow.model.entity.WorkflowRun;
import net.linlan.dsh.domain.workflow.model.entity.WorkflowStartRequest;

/**
 * 工作流服务接口。
 * <p>
 * 执行流程：
 * 1. 上层提交工作流启动请求；
 * 2. 领域层交给工作流引擎端口执行；
 * 3. 返回可取消的运行句柄供后续控制。
 *
 * <p>调用流程：定义工作流 → 启动 → 节点推进 → 事件/状态回写。</p>
 * <p>示例：工作流按节点顺序执行，每个节点可写回下一步上下文。</p>
 *
 */
public interface IWorkflowService {

    /**
     * 启动一个工作流运行。
     * <p>
     * 执行流程：
     * 1. 读取启动请求；
     * 2. 调用工作流引擎创建运行句柄；
     * 3. 返回运行结果的未来对象。
     */
    WorkflowRun start(WorkflowStartRequest request);

    /**
     * 取消正在运行的工作流。
     * <p>
     * 执行流程：
     * 1. 根据 runId 查找运行实例；
     * 2. 将取消原因传给运行实例；
     * 3. 由运行实例完成状态收敛。
     */
    void cancel(String runId, String reason);
}
