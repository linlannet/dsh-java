package net.linlan.dsh.infrastructure.adapter.typert;

import net.linlan.dsh.domain.typert.generator.adapter.port.ITypertGeneratorPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TsMorphTypertGenerator 调用 ts-morph Node 子进程生成类型协议定义。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class TsMorphTypertGenerator implements ITypertGeneratorPort {

    private final String nodeExecutable;
    private final String generatorScriptPath;

    public TsMorphTypertGenerator() {
        this("node", "");
    }

    public TsMorphTypertGenerator(String nodeExecutable, String generatorScriptPath) {
        this.nodeExecutable = nodeExecutable == null || nodeExecutable.isBlank() ? "node" : nodeExecutable;
        this.generatorScriptPath = generatorScriptPath == null ? "" : generatorScriptPath;
    }

    @Override
    public String generate(String tsProjectPath) {
        if (tsProjectPath == null || tsProjectPath.isBlank()) {
            throw new IllegalArgumentException("ts project path required");
        }
        try {
            List<String> command = new ArrayList<>();
            command.add(nodeExecutable);
            if (generatorScriptPath != null && !generatorScriptPath.isBlank()) {
                command.add(generatorScriptPath);
            }
            command.add(tsProjectPath);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);

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
            throw new RuntimeException("failed to invoke ts-morph generator", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ts-morph generation interrupted", e);
        }
    }
}
