package net.linlan.dsh.infrastructure.adapter.typert;

import net.linlan.dsh.domain.typert.adapter.port.IPtcRuntimePort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SandboxedNodePtcProcess 使用 ProcessBuilder 启动 node 子进程执行 PTC 脚本。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class SandboxedNodePtcProcess implements IPtcRuntimePort {

    private final String nodeExecutable;
    private final String sandboxScriptPath;

    public SandboxedNodePtcProcess() {
        this("node", "");
    }

    public SandboxedNodePtcProcess(String nodeExecutable, String sandboxScriptPath) {
        this.nodeExecutable = nodeExecutable == null || nodeExecutable.isBlank() ? "node" : nodeExecutable;
        this.sandboxScriptPath = sandboxScriptPath == null ? "" : sandboxScriptPath;
    }

    @Override
    public String execute(String script, Map<String, Object> env) {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script required");
        }
        try {
            List<String> command = new ArrayList<>();
            command.add(nodeExecutable);
            if (sandboxScriptPath != null && !sandboxScriptPath.isBlank()) {
                command.add(sandboxScriptPath);
            }
            command.add("-e");
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
                        output.append("\n");
                    }
                    output.append(line);
                }
            }
            process.waitFor();
            return output.toString();
        } catch (IOException e) {
            throw new RuntimeException("failed to execute PTC script", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PTC execution interrupted", e);
        }
    }
}
