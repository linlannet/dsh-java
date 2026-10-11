package net.linlan.dsh.domain.task;

import net.linlan.dsh.domain.task.execution.model.valobj.RuntimeExecutionPlanVO;
import net.linlan.dsh.domain.task.queue.model.valobj.QueuedTaskResultVO;

/**
 * Owns queued-task lifecycle behavior.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IHarnessTaskQueueService {

    QueuedTaskResultVO createQueuedTask(String sessionId, RuntimeExecutionPlanVO plan);
}
