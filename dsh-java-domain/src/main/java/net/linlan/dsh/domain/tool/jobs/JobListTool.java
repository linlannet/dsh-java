package net.linlan.dsh.domain.tool.jobs;

import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.jobs.model.valobj.JobSnapshot;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code job_list} 工具：列出当前会话的后台任务。
 * Mirrors TS {@code tool-jobs}.
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class JobListTool implements ToolDefinition {

    private final IJobRegistryService jobs;
    private final String sessionId;

    /**
     * 处理后台作业 列表 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param jobs jobs参数。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */
    public JobListTool(IJobRegistryService jobs, String sessionId) {
        this.jobs = jobs;
        this.sessionId = sessionId;
    }

    @Override public String name() { return "job_list"; }

    @Override
    public String description() {
        return "List background jobs owned by the current session (running, finished, failed, killed).";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "additionalProperties", false, "properties", Map.of());
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of(
                        "jobs", Map.of("type", "array"),
                        "count", Map.of("type", "integer")
                )
        );
    }
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        List<JobSnapshot> list = jobs.list(sessionId);
        if (list.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock("No background jobs for this session."))));
        }
        StringBuilder sb = new StringBuilder("Background jobs:\n");
        for (JobSnapshot j : list) {
            sb.append("- ").append(j.id())
              .append(" [").append(j.status()).append("] ")
              .append(j.label() == null ? "" : j.label())
              .append(" (started ").append(j.startedAt()).append(")\n");
        }
        return CompletableFuture.completedFuture(
                ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
}
