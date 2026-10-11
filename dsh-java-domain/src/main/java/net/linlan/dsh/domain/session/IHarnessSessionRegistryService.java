package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.task.execution.model.valobj.RuntimeExecutionPlanVO;
import net.linlan.dsh.domain.session.registry.model.valobj.SessionOpenedResultVO;

/**
 * Owns session aggregate lifecycle behavior.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public interface IHarnessSessionRegistryService {

    SessionOpenedResultVO openQueuedSession(RuntimeExecutionPlanVO plan);
}
