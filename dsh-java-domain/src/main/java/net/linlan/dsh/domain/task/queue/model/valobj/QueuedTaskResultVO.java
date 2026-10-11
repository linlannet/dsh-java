package net.linlan.dsh.domain.task.queue.model.valobj;

import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import java.time.Instant;
import java.util.List;

/**
 * 排队任务结果对象。
 * <p>
 * 执行流程：
 * 1. 领域层完成任务入队；
 * 2. 组装任务编号、会话编号和状态；
 * 3. 返回给上层作为提交结果。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public record QueuedTaskResultVO(
        String taskId,
        String sessionId,
        HarnessStatusEnumVO status,
        List<String> enabledTools,
        Instant acceptedAt
) {
}
