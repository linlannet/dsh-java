package net.linlan.dsh.infrastructure.adapter.ssh;

import net.linlan.dsh.domain.tool.ssh.SshConnection;
import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * ApacheSshdConnection 使用本地 ssh 命令行实现 ISshPort。
 *
 * <p>不引入 Apache MINA SSHD 依赖，以 ssh 命令行替代。</p>
 * <p>调用流程：connect → execute/readFile/writeFile → disconnect。</p>
 *
 */
public final class ApacheSshdConnection implements ISshPort {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final SshConnection connection;
    private final long timeoutMs;
    private volatile boolean connected = false;

    /**
     * 构造。
     * @param connection SSH 连接信息。
     */
    public ApacheSshdConnection(SshConnection connection) {
        this(connection, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 构造。
     * @param connection SSH 连接信息。
     * @param timeoutMs  超时毫秒。
     */
    public ApacheSshdConnection(SshConnection connection, long timeoutMs) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        this.connection = connection;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    @Override
    public boolean connect() {
        RawResult probe = runSsh("echo __DSH_OK__");
        connected = probe.isSuccess() && probe.stdout().contains("__DSH_OK__");
        return connected;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public SshCommandResult execute(String command) {
        RawResult r = runSsh(command);
        return new SshCommandResult(r.stdout(), r.stderr(), r.exitCode());
    }

    @Override
    public String readFile(String remotePath) {
        SshCommandResult r = execute("cat " + shellQuote(remotePath));
        return r.isSuccess() ? r.stdout() : "";
    }

    @Override
    public boolean writeFile(String remotePath, String content) {
        String safe = content == null ? "" : content.replace("'", "'\\''");
        String cmd = "printf '%s' '" + safe + "' > " + shellQuote(remotePath);
        return execute(cmd).isSuccess();
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

    private RawResult runSsh(String remoteCommand) {
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
                return new RawResult(stdout, stderr + "\n[timeout]", -1);
            }
            return new RawResult(stdout, stderr, process.exitValue());
        } catch (IOException e) {
            return new RawResult("", "IO error: " + e.getMessage(), -1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RawResult("", "interrupted", -1);
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

    private record RawResult(String stdout, String stderr, int exitCode) {
        boolean isSuccess() { return exitCode == 0; }
    }
}
