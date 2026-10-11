package net.linlan.dsh.domain.task.submission.service.filter;

import net.linlan.dsh.domain.task.submission.model.valobj.TaskSubmissionVO;
import org.springframework.stereotype.Service;

/**
 * Rejects malformed tool codes before tool resolution.
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class ToolSelectionFilter implements TaskSubmissionFilter {

    @Override
    public void filter(TaskSubmissionVO submission) {
        boolean invalid = submission.toolSelection().toolCodes().stream()
                .anyMatch(toolCode -> toolCode == null || toolCode.isBlank());
        if (invalid) {
            throw new IllegalArgumentException("requestedTools contains blank tool code");
        }
    }
}
