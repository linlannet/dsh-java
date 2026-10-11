package net.linlan.dsh.domain.tool.jobs;

import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.jobs.model.valobj.JobSnapshot;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code job_output} 工具：读取某个后台任务的状态与输出。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class JobOutputTool implements ToolDefinition {

    private static final int MAX_OUTPUT = 20_000;

    private final IJobRegistryService jobs;
    private final String sessionId;

    /**
     * 处理后台作业 Output 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param jobs jobs参数。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */
    public JobOutputTool(IJobRegistryService jobs, String sessionId) {
        this.jobs = jobs;
        this.sessionId = sessionId;
    }

    @Override public String name() { return "job_output"; }

    @Override
    public String description() {
        return "Read the status and captured output of a background job by id.";
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
                        "job_id", Map.of("type", "string", "description", "The job id returned when the job was started.")
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
                        "output", Map.of("type", "string")
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
        if (id.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("job_id parameter is required", "MISSING_PARAM"));
        }
        Optional<JobSnapshot> job = jobs.read(id, sessionId);
        if (job.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("job not found: " + id, "JOB_NOT_FOUND"));
        }
        JobSnapshot j = job.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Job ").append(j.id()).append(" [").append(j.status()).append("]\n");
        if (j.detail() != null) sb.append("Detail: ").append(j.detail()).append("\n");
        String output = j.output();
        if (output != null) {
            if (output.length() > MAX_OUTPUT) {
                output = output.substring(output.length() - MAX_OUTPUT)
                        + "\n... [truncated, " + j.output().length() + " chars total]";
            }
            sb.append("--- output ---\n").append(output);
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
