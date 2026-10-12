package net.linlan.dsh.domain.tool.shell.persistent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PersistentShellTool 持久化 Shell 工具，跨调用保持 Shell 状态。
 *
 * <p>调用流程：启动 bash/sh 进程 → 通过 stdin/stdout 交互 → 保持环境与工作目录。</p>
 * <p>示例：execute("cd /tmp") 后 execute("pwd") 返回 /tmp。</p>
 *
 */
public final class PersistentShellTool implements AutoCloseable {

    private static final long DEFAULT_TIMEOUT_MS = 10_000L;
    private static final String MARKER = "__DSH_CMD_END__";

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final BufferedReader errorReader;
    private final Map<String, String> envOverrides = new LinkedHashMap<>();
    private final long defaultTimeoutMs;
    private volatile boolean closed = false;

    /**
     * 默认构造，启动 bash。
     */
    public PersistentShellTool() {
        this(defaultShell(), DEFAULT_TIMEOUT_MS);
    }

    /**
     * 自定义构造。
     * @param shellCommand    shell 命令（如 bash/sh）。
     * @param defaultTimeoutMs 默认超时毫秒。
     */
    public PersistentShellTool(String shellCommand, long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : DEFAULT_TIMEOUT_MS;
        try {
            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.redirectErrorStream(false);
            this.process = pb.start();
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("failed to start shell: " + e.getMessage(), e);
        }
    }

    private static String defaultShell() {
        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return shell;
        }
        return "/bin/sh";
    }

    /**
     * 执行命令并返回结果。
     * @param command 命令。
     * @return 执行结果。
     */
    public ExecutionResult execute(String command) {
        return execute(command, defaultTimeoutMs);
    }

    /**
     * 执行命令带超时。
     * @param command   命令。
     * @param timeoutMs 超时毫秒。
     * @return 执行结果。
     */
    public ExecutionResult execute(String command, long timeoutMs) {
        if (closed) {
            return new ExecutionResult("", "shell is closed", -1);
        }
        if (command == null || command.isBlank()) {
            return new ExecutionResult("", "", 0);
        }
        long timeout = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        try {
            writer.write(command + "\n");
            writer.write("echo " + MARKER + "\n");
            writer.flush();

            StringBuilder stdout = new StringBuilder();
            long deadline = System.currentTimeMillis() + timeout;
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine();
                if (line == null) {
                    return new ExecutionResult(stdout.toString(), "stream closed", -1);
                }
                if (line.endsWith(MARKER)) {
                    break;
                }
                if (stdout.length() > 0) {
                    stdout.append("\n");
                }
                stdout.append(line);
            }
            String stderr = drainError();
            return new ExecutionResult(stdout.toString(), stderr, 0);
        } catch (IOException e) {
            return new ExecutionResult("", "IO error: " + e.getMessage(), -1);
        }
    }

    private String drainError() {
        StringBuilder sb = new StringBuilder();
        try {
            while (errorReader.ready()) {
                String line = errorReader.readLine();
                if (line == null) break;
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }

    /**
     * 设置环境变量。
     * @param key   键。
     * @param value 值。
     */
    public void setEnv(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("env key must not be blank");
        }
        envOverrides.put(key, value);
        execute("export " + key + "=" + shellQuote(value), defaultTimeoutMs);
    }

    /**
     * 切换工作目录。
     * @param path 路径。
     */
    public void cd(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        execute("cd " + shellQuote(path), defaultTimeoutMs);
    }

    /**
     * 返回当前工作目录。
     * @return pwd 输出。
     */
    public String pwd() {
        ExecutionResult r = execute("pwd");
        return r.stdout().trim();
    }

    /**
     * 获取环境变量覆盖快照。
     * @return 不可变 Map。
     */
    public Map<String, String> envOverrides() {
        return Map.copyOf(envOverrides);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.write("exit\n");
            writer.flush();
        } catch (IOException ignored) {
        }
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        process.destroyForcibly();
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

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
}
