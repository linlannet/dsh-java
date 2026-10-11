package net.linlan.dsh.domain.tool.jobs;

import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code job_kill} 工具：取消一个仍在运行的后台任务。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class JobKillTool implements ToolDefinition {

    private final IJobRegistryService jobs;
    private final String sessionId;

    /**
     * 处理后台作业 Kill 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param jobs jobs参数。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */
    public JobKillTool(IJobRegistryService jobs, String sessionId) {
        this.jobs = jobs;
        this.sessionId = sessionId;
    }

    @Override public String name() { return "job_kill"; }

    @Override
    public String description() {
        return "Cancel a running background job owned by the current session.";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "job_id", Map.of("type", "string", "description", "The job id to cancel."),
                        "reason", Map.of("type", "string", "description", "Optional cancellation reason.")
                ),
                "required", List.of("job_id")
        );
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
                        "job_id", Map.of("type", "string"),
                        "killed", Map.of("type", "boolean")
                )
        );
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        Map<String, Object> argMap = args instanceof Map ? (Map<String, Object>) args : Map.of();
        String id = argMap.get("job_id") == null ? "" : String.valueOf(argMap.get("job_id")).trim();
        String reason = argMap.get("reason") == null ? "cancelled by agent" : String.valueOf(argMap.get("reason"));
        if (id.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("job_id parameter is required", "MISSING_PARAM"));
        }
        boolean killed = jobs.cancel(id, sessionId, reason);
        if (!killed) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("job not running or not found: " + id, "JOB_NOT_RUNNING"));
        }
        return CompletableFuture.completedFuture(
                ToolExecutionResult.ok(List.of(new TextBlock("Job " + id + " cancelled (" + reason + ")"))));
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return false; }
}
