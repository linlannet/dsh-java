package net.linlan.dsh.domain.tool.ssh;

import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * SubprocessSsh 通过本地 ssh 子进程执行远程命令，实现 ISshPort。
 *
 * <p>调用流程：构建 ssh 命令行 → ProcessBuilder 执行 → 收集输出。</p>
 *
 */
public final class SubprocessSsh implements ISshPort {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final SshConnection connection;
    private final long timeoutMs;
    private volatile boolean connected = false;

    /**
     * 构造。
     * @param connection SSH 连接信息。
     */
    public SubprocessSsh(SshConnection connection) {
        this(connection, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 构造。
     * @param connection SSH 连接信息。
     * @param timeoutMs  超时毫秒。
     */
    public SubprocessSsh(SshConnection connection, long timeoutMs) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    @Override
    public boolean connect() {
        ISshCommandResult probe = runSsh("echo ok");
        connected = probe.isSuccess() && "ok".equals(probe.stdout().trim());
        return connected;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public SshCommandResult execute(String command) {
        ISshCommandResult r = runSsh(command);
        return new SshCommandResult(r.stdout(), r.stderr(), r.exitCode());
    }

    @Override
    public String readFile(String remotePath) {
        SshCommandResult r = execute("cat " + shellQuote(remotePath));
        return r.isSuccess() ? r.stdout() : "";
    }

    @Override
    public boolean writeFile(String remotePath, String content) {
        String safeContent = content == null ? "" : content.replace("'", "'\\''");
        String cmd = "printf '%s' '" + safeContent + "' > " + shellQuote(remotePath);
        SshCommandResult r = execute(cmd);
        return r.isSuccess();
    }

    /**
     * 返回连接信息。
     * @return SshConnection。
     */
    public SshConnection connection() {
        return connection;
    }

    /**
     * 判断是否已连接。
     * @return true 表示已连接。
     */
    public boolean isConnected() {
        return connected;
    }

    private ISshCommandResult runSsh(String remoteCommand) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c",
                    connection.toCommandLine() + " " + shellQuote(remoteCommand));
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout = readStream(new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
            String stderr = readStream(new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)));

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ISshCommandResult(stdout, stderr + "\n[timeout]", -1);
            }
            return new ISshCommandResult(stdout, stderr, process.exitValue());
        } catch (IOException e) {
            return new ISshCommandResult("", "IO error: " + e.getMessage(), -1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ISshCommandResult("", "interrupted", -1);
        }
    }

    private static String readStream(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (!first) sb.append("\n");
            sb.append(line);
            first = false;
        }
        return sb.toString();
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private record ISshCommandResult(String stdout, String stderr, int exitCode) {
        boolean isSuccess() { return exitCode == 0; }
    }
}
