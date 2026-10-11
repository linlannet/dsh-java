package net.linlan.dsh.domain.tool.jobs;

import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.jobs.model.entity.JobStart;
import net.linlan.dsh.domain.jobs.model.valobj.JobKind;
import net.linlan.dsh.domain.jobs.model.valobj.JobOutcome;
import net.linlan.dsh.domain.jobs.model.valobj.JobSnapshot;
import net.linlan.dsh.domain.jobs.model.valobj.JobStatus;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.tool.shell.IShellExecutor;
import net.linlan.dsh.domain.tool.shell.ShellResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code job_run} 工具：以后台任务方式执行 shell 命令（不阻塞当前回合）。
 * <p>
 * 执行流程：
 * 1. 接收命令并登记到 {@link IJobRegistryService}；
 * 2. 任务在线程池中异步执行，输出在结束时一次性捕获；
 * 3. 立即返回 job_id，模型可用 job_output 轮询结果。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class JobRunTool implements ToolDefinition {

    private static final long DEFAULT_TIMEOUT_MS = 30L * 60 * 1000; // 30 min cap for background jobs

    private final IJobRegistryService jobs;
    private final IShellExecutor shellExecutor;
    private final String sessionId;
    private final String defaultCwd;

    /**
     * 处理后台作业 Run 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param jobs jobs参数。
     * @param shellExecutor Shell Executor参数。
     * @param sessionId 会话 ID。
     * @param defaultCwd default Cwd参数。
     * @return 方法执行结果。
     */
    public JobRunTool(IJobRegistryService jobs, IShellExecutor shellExecutor, String sessionId, String defaultCwd) {
        this.jobs = jobs;
        this.shellExecutor = shellExecutor;
        this.sessionId = sessionId;
        this.defaultCwd = defaultCwd;
    }

    @Override public String name() { return "job_run"; }

    @Override
    public String description() {
        return "Run a shell command in the background as a job and return immediately with a job_id. " +
                "Use for long-running commands (builds, servers, watchers). " +
                "Poll with job_output and cancel with job_kill.";
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
                        "command", Map.of("type", "string", "description", "The shell command to run in background."),
                        "label", Map.of("type", "string", "description", "Optional human-readable label."),
                        "cwd", Map.of("type", "string", "description", "Optional working directory."),
                        "timeout_ms", Map.of("type", "integer", "description", "Optional cap, default 30 minutes.")
                ),
                "required", List.of("command")
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
        try {
            Map<String, Object> argMap = (Map<String, Object>) args;
            String command = argMap.get("command") == null ? "" : String.valueOf(argMap.get("command"));
            if (command.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("command parameter is required", "MISSING_PARAM"));
            }
            String label = argMap.get("label") == null ? command : String.valueOf(argMap.get("label"));
            String cwd = argMap.get("cwd") == null ? defaultCwd : String.valueOf(argMap.get("cwd"));
            long timeoutMs = argMap.get("timeout_ms") != null
                    ? ((Number) argMap.get("timeout_ms")).longValue() : DEFAULT_TIMEOUT_MS;

            JobSnapshot snapshot = jobs.start(new JobStart(JobKind.BASH, label, sessionId, () -> {
                ShellResult r = shellExecutor.execute(command, cwd, timeoutMs);
                String output = "Exit code: " + r.exitCode()
                        + "\n--- stdout ---\n" + (r.stdout() == null ? "" : r.stdout())
                        + "\n--- stderr ---\n" + (r.stderr() == null ? "" : r.stderr());
                return new JobOutcome(r.isSuccess() ? JobStatus.COMPLETED : JobStatus.FAILED,
                        "exit code " + r.exitCode(), output);
            }));

            return CompletableFuture.completedFuture(ToolExecutionResult.ok(List.of(new TextBlock(
                    "Started background job " + snapshot.id() + " [" + snapshot.status() + "]: " + label
                            + "\nUse job_output(job_id=\"" + snapshot.id() + "\") to check progress."))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("job_run failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
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
