package net.linlan.dsh.infrastructure.adapter.shell;

import net.linlan.dsh.domain.tool.shell.IShellExecutor;
import net.linlan.dsh.domain.tool.shell.ShellResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Local shell executor using {@link ProcessBuilder}.
 * <p>
 * Runs commands via the system shell ({@code /bin/sh} on Unix,
 * {@code cmd.exe} on Windows). Captures stdout and stderr separately.
 *
 *
 */
public class LocalShellExecutor implements IShellExecutor {

    private static final String SHELL = System.getProperty("os.name").toLowerCase().contains("win")
            ? "cmd.exe"
            : "/bin/sh";
    private static final String SHELL_FLAG = System.getProperty("os.name").toLowerCase().contains("win")
            ? "/c"
            : "-c";

    @Override
    public ShellResult execute(String command, String cwd, long timeoutMs) {
        long start = System.currentTimeMillis();

        ProcessBuilder pb = new ProcessBuilder(SHELL, SHELL_FLAG, command);
        pb.redirectErrorStream(false);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new java.io.File(cwd));
        }

        try {
            Process process = pb.start();

            // Read stdout and stderr in separate threads to avoid deadlock
            StringBuilder stdoutBuf = new StringBuilder();
            StringBuilder stderrBuf = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdoutBuf));
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderrBuf));
            stdoutThread.start();
            stderrThread.start();

            boolean finished;
            if (timeoutMs > 0) {
                finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    stdoutThread.interrupt();
                    stderrThread.interrupt();
                    long duration = System.currentTimeMillis() - start;
                    return new ShellResult(
                            stdoutBuf.toString(),
                            stderrBuf.toString() + "\n[process killed: timeout after " + timeoutMs + "ms]",
                            -1,
                            duration
                    );
                }
            } else {
                process.waitFor();
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - start;
            return new ShellResult(stdoutBuf.toString(), stderrBuf.toString(), exitCode, duration);

        } catch (IOException e) {
            long duration = System.currentTimeMillis() - start;
            return new ShellResult("", "IO error: " + e.getMessage(), -2, duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - start;
            return new ShellResult("", "Interrupted: " + e.getMessage(), -3, duration);
        }
    }

    /**
     * 读取 流。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param is is参数。
     * @param buf buf参数。
     */
    private void readStream(InputStream is, StringBuilder buf) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (buf.length() > 0) buf.append("\n");
                buf.append(line);
            }
        } catch (IOException e) {
            if (buf.length() > 0) buf.append("\n");
            buf.append("[read error: ").append(e.getMessage()).append("]");
        }
    }
}
