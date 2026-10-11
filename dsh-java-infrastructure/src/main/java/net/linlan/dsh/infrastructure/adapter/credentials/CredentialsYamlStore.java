package net.linlan.dsh.infrastructure.adapter.credentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal YAML key=value store for credential files — no external dependency.
 * Format: {@code KEY: value} per line, blank lines and comments ignored.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
final class CredentialsYamlStore {

    /**
     * 处理Credentials YAML Store。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    private CredentialsYamlStore() {}

    static Map<String, String> parse(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int sep = trimmed.indexOf(':');
            if (sep <= 0) continue;
            String key = trimmed.substring(0, sep).trim();
            String val = trimmed.substring(sep + 1).trim();
            if (!key.isEmpty()) result.put(key, val);
        }
        return result;
    }
    /**
     * 写入目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param path 文件或资源路径。
     * @param Map<String Map String参数。
     * @param store store参数。
     */


    static void write(Path path, Map<String, String> store) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# DeepSeek Harness credentials\n");
        sb.append("# Writable via the credentials seam. Do not edit manually unless necessary.\n");
        for (Map.Entry<String, String> entry : store.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        Files.writeString(path, sb);
        // 0600: owner-only
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
}
