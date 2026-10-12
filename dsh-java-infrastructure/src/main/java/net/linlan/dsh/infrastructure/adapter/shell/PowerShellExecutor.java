package net.linlan.dsh.infrastructure.adapter.shell;

import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PowerShellExecutor 实现 PowerShell 本地与沙箱执行。
 *
 * <p>调用流程：构建 ProcessBuilder → 执行 pwsh → 收集输出 → 返回 ExecutionResult。</p>
 * <p>示例：execute("Get-Date") 返回当前日期 stdout。</p>
 *
 */
public final class PowerShellExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final String powershellCommand;
    private final long defaultTimeoutMs;

    /**
     * ExecutionResult 记录执行结果。
     */
    public record ExecutionResult(String stdout, String stderr, int exitCode) {
        /**
         * 判断是否成功。
         * @return true 表示退出码为 0。
         */
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * 默认构造，使用 pwsh 与 30s 超时。
     */
    public PowerShellExecutor() {
        this("pwsh", DEFAULT_TIMEOUT_MS);
    }

    /**
     * 自定义构造。
     * @param powershellCommand pwsh 命令路径。
     * @param defaultTimeoutMs  默认超时毫秒。
     */
    public PowerShellExecutor(String powershellCommand, long defaultTimeoutMs) {
        this.powershellCommand = powershellCommand == null ? "pwsh" : powershellCommand;
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : DEFAULT_TIMEOUT_MS;
    }

    /**
     * 执行 PowerShell 脚本。
     * @param script 脚本内容。
     * @return 执行结果。
     */
    public ExecutionResult execute(String script) {
        return execute(script, defaultTimeoutMs);
    }

    /**
     * 执行 PowerShell 脚本带超时。
     * @param script    脚本内容。
     * @param timeoutMs 超时毫秒。
     * @return 执行结果。
     */
    public ExecutionResult execute(String script, long timeoutMs) {
        if (script == null) {
            return new ExecutionResult("", "script is null", -1);
        }
        long timeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    powershellCommand, "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout = readStream(new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
            String stderr = readStream(new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)));

            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(stdout, stderr + "\n[timeout after " + timeout + "ms]", -1);
            }
            return new ExecutionResult(stdout, stderr, process.exitValue());
        } catch (IOException e) {
            return new ExecutionResult("", "IO error: " + e.getMessage(), -1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult("", "interrupted", -1);
        }
    }

    /**
     * 在沙箱策略下执行 PowerShell 脚本。
     * @param script 脚本内容。
     * @param policy 沙箱策略。
     * @return 执行结果。
     */
    public ExecutionResult executeInSandbox(String script, SandboxExecutionPolicy policy) {
        if (script == null) {
            return new ExecutionResult("", "script is null", -1);
        }
        if (policy == null) {
            return execute(script);
        }
        return switch (policy.mode()) {
            case READ_ONLY -> executeReadOnly(script, policy);
            case WORKSPACE_WRITE -> executeWorkspaceWrite(script, policy);
            case DANGER_FULL_ACCESS -> execute(script);
        };
    }

    private ExecutionResult executeReadOnly(String script, SandboxExecutionPolicy policy) {
        String wrapped = "$ErrorActionPreference='Stop'; " + script;
        ExecutionResult raw = execute(wrapped);
        return new ExecutionResult(raw.stdout(), raw.stderr(), raw.exitCode());
    }

    private ExecutionResult executeWorkspaceWrite(String script, SandboxExecutionPolicy policy) {
        List<String> envLines = new ArrayList<>();
        policy.workspaceRoots().forEach(root ->
                envLines.add("$env:DSH_WORKSPACE_ROOT += '" + root.toString().replace("'", "''") + ";'"));
        String prefix = String.join("; ", envLines);
        String wrapped = prefix.isEmpty() ? script : prefix + "; " + script;
        return execute(wrapped);
    }

    private static String readStream(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (!first) {
                sb.append("\n");
            }
            sb.append(line);
            first = false;
        }
        return sb.toString();
    }

    /**
     * 探测当前平台是否可用 PowerShell。
     * @return true 表示 pwsh 命令可执行。
     */
    public boolean isAvailable() {
        ExecutionResult r = execute("Write-Output ok");
        return r.isSuccess() && "ok".equals(r.stdout().trim());
    }
}
