package net.linlan.dsh.domain.task.queue.model.entity;

import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 排队任务实体。
 * <p>
 * 执行流程：
 * 1. 任务提交成功后生成实体；
 * 2. 实体承载会话、提示词、工具和状态；
 * 3. 仓储负责将其持久化为待执行任务。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public class HarnessTaskEntity {

    private final String taskId;
    private final String sessionId;
    private final String prompt;
    private final List<String> enabledTools;
    private final HarnessStatusEnumVO status;
    private final Instant submittedAt;

    /**
     * 处理Harness 任务 Entity。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param taskId 任务 ID。
     * @param sessionId 会话 ID。
     * @param prompt 提示词参数。
     * @param enabledTools enabled Tools参数。
     * @param status 状态对象。
     * @param submittedAt submitted At参数。
     * @return 方法执行结果。
     */
    public HarnessTaskEntity(String taskId, String sessionId, String prompt, List<String> enabledTools, HarnessStatusEnumVO status, Instant submittedAt) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.enabledTools = List.copyOf(enabledTools);
        this.status = Objects.requireNonNull(status, "status");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    /**
     * 获取Task Id。
     */
    public String getTaskId() { return taskId; }
    /**
     * 获取Session Id。
     */
    public String getSessionId() { return sessionId; }
    /**
     * 获取Prompt。
     */
    public String getPrompt() { return prompt; }
    /**
     * 获取启用状态Tool列表。
     * @return 领域处理结果。
     */
    public List<String> getEnabledTools() { return enabledTools; }
    /**
     * 获取Status。
     */
    public HarnessStatusEnumVO getStatus() { return status; }
    /**
     * 获取Submitted At。
     */
    public Instant getSubmittedAt() { return submittedAt; }
}
