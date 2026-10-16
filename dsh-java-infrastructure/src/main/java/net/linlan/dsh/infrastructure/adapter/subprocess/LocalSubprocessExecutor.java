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
            pb.directory(new java.io.File(spec.cwd()));
            Map<String, String> env = pb.environment();
            env.clear();
            env.putAll(spec.envOverride());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdout.length() > OUTPUT_BOUND) {
                        return SubprocessResult.spilled(process.waitFor(), "输出超限，已 spill 落盘");
                    }
                    stdout.append(line).append('\n');
                }
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
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
