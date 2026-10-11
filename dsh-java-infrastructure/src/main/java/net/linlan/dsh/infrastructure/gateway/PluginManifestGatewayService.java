package net.linlan.dsh.infrastructure.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reads plugin manifest files from the local filesystem.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Service
public class PluginManifestGatewayService {

    private final ObjectMapper objectMapper;

    /**
     * 处理插件 清单 Gateway Service。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public PluginManifestGatewayService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads one JSON file into a generic key/value map when it exists.
     *
     * @param path file path
     * @return parsed manifest map when present
     */
    public Optional<Map<String, Object>> readJson(Path path) {
        if (!Files.exists(path) || Files.isDirectory(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), new TypeReference<>() { }));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to parse manifest: " + path, exception);
        }
    }
}
