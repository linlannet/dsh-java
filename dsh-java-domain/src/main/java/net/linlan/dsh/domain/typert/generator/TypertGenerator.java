package net.linlan.dsh.domain.typert.generator;

import net.linlan.dsh.domain.typert.generator.adapter.port.ITypertGeneratorPort;

/**
 * TypertGenerator 从 TypeScript 项目生成类型协议定义。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class TypertGenerator {

    private final ITypertGeneratorPort port;

    public TypertGenerator(ITypertGeneratorPort port) {
        if (port == null) {
            throw new IllegalArgumentException("generator port required");
        }
        this.port = port;
    }

    public String generate(String tsProjectPath) {
        if (tsProjectPath == null || tsProjectPath.isBlank()) {
            throw new IllegalArgumentException("ts project path required");
        }
        String raw = port.generate(tsProjectPath);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw;
    }

    public String generate(String tsProjectPath, java.util.Map<String, String> options) {
        if (tsProjectPath == null || tsProjectPath.isBlank()) {
            throw new IllegalArgumentException("ts project path required");
        }
        String raw = port.generate(tsProjectPath);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (options == null || options.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw);
        sb.append("\n# options:\n");
        for (var entry : options.entrySet()) {
            sb.append("# ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}
