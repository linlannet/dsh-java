package net.linlan.dsh.infrastructure.adapter.typert;

import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import net.linlan.dsh.domain.typert.model.entity.TypertContribution;
import net.linlan.dsh.domain.typert.model.valobj.TypertFace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads typert protocol declarations from JSON files.
 *
 * <p>JSON format:
 * <pre>
 * {
 *   "package": "my-plugin",
 *   "face": "host",
 *   "invocations": [
 *     { "namespace": "myPlugin", "method": "doWork",
 *       "paramsSchema": {...}, "resultSchema": {...}, "description": "..." }
 *   ],
 *   "schemas": [{"name": "MyInput", "schema": {...}}]
 * }
 * </pre>
 *
 *
 */
public class TypertProtocolLoader {

    private final ObjectMapper mapper;

    /**
     * 处理Typert Protocol Loader。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param mapper mapper参数。
     * @return 方法执行结果。
     */
    public TypertProtocolLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }
    /**
     * 加载。
     */

    public TypertContribution load(Path protocolFile) throws IOException {
        JsonNode root = mapper.readTree(Files.readString(protocolFile));
        String packageName = root.path("package").asText();
        TypertFace face = TypertFace.valueOf(root.path("face").asText("HOST").toUpperCase());

        List<InvocationDescriptor> invocations = new ArrayList<>();
        for (JsonNode invNode : root.path("invocations")) {
            invocations.add(new InvocationDescriptor(
                    invNode.path("namespace").asText(),
                    invNode.path("method").asText(),
                    toMap(invNode.path("paramsSchema")),
                    toMap(invNode.path("resultSchema")),
                    invNode.path("description").asText(null)
            ));
        }

        List<Map.Entry<String, Map<String, Object>>> schemas = new ArrayList<>();
        for (JsonNode schemaNode : root.path("schemas")) {
            schemas.add(Map.entry(
                    schemaNode.path("name").asText(),
                    toMap(schemaNode.path("schema"))
            ));
        }

        return new TypertContribution(packageName, face, schemas, invocations);
    }

    /**
     * 转换为 映射。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param node 策略树节点。
     * @return 转换后的目标对象。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return mapper.convertValue(node, Map.class);
    }
}
