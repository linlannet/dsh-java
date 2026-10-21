package net.linlan.dsh.infrastructure.adapter.typert;

import net.linlan.dsh.domain.typert.adapter.port.IPtcRuntimePort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CpythonPtcAdapter 委托外部 CPython 3.10+ 进程执行 PTC 脚本。
 *
 * <p>仅在 macOS 或 Linux 上启用；CPython 不可用时返回运行时不可用错误。每次执行为全新进程，运行间无状态保留。</p>
 *
 */
public class CpythonPtcAdapter implements IPtcRuntimePort {

    private final String pythonExecutable;

    public CpythonPtcAdapter() {
        this("python3");
    }

    public CpythonPtcAdapter(String pythonExecutable) {
        this.pythonExecutable = (pythonExecutable == null || pythonExecutable.isBlank()) ? "python3" : pythonExecutable;
    }

    /**
     * 当前平台是否支持 CPython PTC 运行时（macOS 或 Linux）。
     *
     * @return 支持返回 true
     */
    public boolean platformSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("linux") || os.contains("nux") || os.contains("nix");
    }

    @Override
    public String execute(String script, Map<String, Object> env) {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script required");
        }
        if (!platformSupported()) {
            throw new IllegalStateException("CPython PTC runtime unavailable on this platform");
        }
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add("-c");
            command.add(script);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            if (env != null) {
                for (var entry : env.entrySet()) {
                    if (entry.getValue() != null) {
                        builder.environment().put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            }

            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
            process.waitFor();
            return output.toString();
        } catch (IOException e) {
            throw new IllegalStateException("CPython runtime unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("CPython PTC execution interrupted", e);
        }
    }
}