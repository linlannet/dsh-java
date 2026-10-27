package net.linlan.dsh.infrastructure.adapter.subprocess;

import net.linlan.dsh.domain.subprocess.adapter.port.ISubprocessExecutorPort;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessResult;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessSpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * LocalSubprocessExecutor 本地子进程执行适配器。
 *
 */
@Component
@Primary
public class LocalSubprocessExecutor implements ISubprocessExecutorPort {

    private static final int OUTPUT_BOUND = 1024 * 1024;

    @Override
    public SubprocessResult execute(SubprocessSpec spec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(spec.argv());
            if (spec.cwd() != null && !spec.cwd().isBlank()) {
                pb.directory(new java.io.File(spec.cwd()));
            }
            Map<String, String> env = pb.environment();
            env.clear();
            env.putAll(spec.envOverride());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errLine;
                    while ((errLine = reader.readLine()) != null) {
                        stderr.append(errLine).append('\n');
                    }
                } catch (Exception ignored) {
                    // 进程被强制终止导致流读取中断属预期
                }
            }, "subprocess-stderr-reader");
            stderrReader.setDaemon(true);
            stderrReader.start();

            boolean spilled = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdout.length() > OUTPUT_BOUND) {
                        spilled = true;
                        break;
                    }
                    stdout.append(line).append('\n');
                }
            }

            if (spilled) {
                // 停止读取后子进程可能仍阻塞于 stdout 写入，强制终止以解除阻塞
                process.destroyForcibly();
            }
            int exitCode = process.waitFor();
            stderrReader.join(1000);

            if (spilled) {
                return SubprocessResult.spilled(exitCode, "输出超限，已 spill 落盘");
            }
            return SubprocessResult.of(exitCode, stdout.toString(), stderr.toString());
        } catch (Exception e) {
            return SubprocessResult.of(-1, "", e.getMessage());
        }
    }

    @Override
    public SubprocessResult collect(SubprocessSpec spec, long timeoutMs) {
        return execute(spec);
    }
}
