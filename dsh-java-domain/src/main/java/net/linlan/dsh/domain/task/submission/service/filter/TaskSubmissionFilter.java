package net.linlan.dsh.domain.task.submission.service.filter;

import net.linlan.dsh.domain.task.submission.model.valobj.TaskSubmissionVO;

/**
 * Defines one validation step over a task submission request.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface TaskSubmissionFilter {

    /**
     * 处理filter。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param submission submission。
     * @return 领域处理结果。
     */
    void filter(TaskSubmissionVO submission);
}
