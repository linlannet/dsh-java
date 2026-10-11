package net.linlan.dsh.infrastructure.adapter.tool;

import net.linlan.dsh.domain.tool.service.ToolArgumentsParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Parses model tool-call arguments with Jackson.
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 */
public class JacksonToolArgumentsParser implements ToolArgumentsParser {

    private final ObjectMapper objectMapper;

    /**
     * 处理Jackson 工具 Arguments Parser。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public JacksonToolArgumentsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object parse(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, Object.class);
        } catch (Exception ignored) {
            return arguments;
        }
    }
}
