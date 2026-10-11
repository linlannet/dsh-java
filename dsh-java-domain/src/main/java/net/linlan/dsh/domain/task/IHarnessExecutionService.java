package net.linlan.dsh.domain.task;

import net.linlan.dsh.domain.task.execution.model.valobj.HarnessExecutionResultVO;

/**
 * Owns runtime execution for accepted sessions.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IHarnessExecutionService {

    /**
     * 执行领域动作并返回结果。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sessionId 会话ID。
     * @return 领域处理结果。
     */
    HarnessExecutionResultVO executeSession(String sessionId);
}
