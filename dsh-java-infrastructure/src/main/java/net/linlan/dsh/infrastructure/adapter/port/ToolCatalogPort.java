package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.runtime.tool.adapter.port.IToolCatalogPort;
import net.linlan.dsh.infrastructure.gateway.ToolCatalogGatewayService;
import net.linlan.dsh.infrastructure.gateway.dto.ToolCatalogRequestDTO;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 工具目录端口适配器。
 * <p>
 * 执行流程：
 * 1. 接收领域层的工具筛选请求；
 * 2. 调用网关层或本地规则完成过滤；
 * 3. 返回可启用工具列表。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Component
public class ToolCatalogPort implements IToolCatalogPort {

    private final ToolCatalogGatewayService toolCatalogGatewayService;

    /**
     * 转换为 ol Catalog Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param toolCatalogGatewayService 工具 Catalog Gateway Service参数。
     * @return 方法执行结果。
     */
    public ToolCatalogPort(ToolCatalogGatewayService toolCatalogGatewayService) {
        this.toolCatalogGatewayService = toolCatalogGatewayService;
    }

    @Override
    public List<String> resolveEnabledTools(List<String> requestedTools) {
        return toolCatalogGatewayService.resolve(new ToolCatalogRequestDTO(requestedTools)).enabledTools();
    }
}
