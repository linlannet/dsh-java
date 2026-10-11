package net.linlan.dsh.infrastructure.gateway.dto;

import java.util.List;

/**
 * Carries requested tool codes to the gateway layer.
 *
 * @param requestedTools requested tool codes
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record ToolCatalogRequestDTO(List<String> requestedTools) {

    public ToolCatalogRequestDTO {
        requestedTools = requestedTools == null ? List.of() : List.copyOf(requestedTools);
    }
}
