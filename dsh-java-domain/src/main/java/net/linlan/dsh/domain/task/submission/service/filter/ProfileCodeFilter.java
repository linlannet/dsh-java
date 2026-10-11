package net.linlan.dsh.domain.task.submission.service.filter;

import net.linlan.dsh.domain.task.submission.model.valobj.TaskSubmissionVO;
import org.springframework.stereotype.Service;

/**
 * Rejects requests that do not pick an execution profile.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class ProfileCodeFilter implements TaskSubmissionFilter {

    @Override
    public void filter(TaskSubmissionVO submission) {
        if (submission.profileCode() == null || submission.profileCode().isBlank()) {
            throw new IllegalArgumentException("profileCode must not be blank");
        }
    }
}
