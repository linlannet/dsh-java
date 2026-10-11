package net.linlan.dsh.domain.task.submission.service.filter;

import net.linlan.dsh.domain.task.submission.model.valobj.TaskSubmissionVO;
import org.springframework.stereotype.Service;

/**
 * Rejects empty prompts before any domain state is created.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class PromptNotBlankFilter implements TaskSubmissionFilter {

    @Override
    public void filter(TaskSubmissionVO submission) {
        if (submission.prompt() == null || submission.prompt().isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
    }
}
