package net.linlan.dsh.domain.tool.mcp.resource.service;

import net.linlan.dsh.domain.tool.mcp.resource.adapter.port.IMcpResourcePort;
import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * McpResourceDiscovery 表示MCP资源发现服务，按 scope 过滤资源。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class McpResourceDiscovery {

    private final IMcpResourcePort port;

    /**
     * 处理Mcp Resource Discovery。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param port port参数。
     * @return 方法执行结果。
     */
    public McpResourceDiscovery(IMcpResourcePort port) {
        this.port = Objects.requireNonNull(port, "port must not be null");
    }

    /**
     * 处理discover。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param scope scope参数。
     * @return 方法执行结果。
     */
    public List<McpResource> discover(String scope) {
        List<McpResource> resources = port.listResources(scope);
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        if (scope == null || scope.isBlank()) {
            return resources;
        }
        List<McpResource> filtered = new ArrayList<>();
        for (McpResource resource : resources) {
            if (scope.equals(resource.scope())) {
                filtered.add(resource);
            }
        }
        return filtered;
    }

    /**
     * 处理read。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param uri uri参数。
     * @return 方法执行结果。
     */
    public String read(String uri) {
        return port.readResource(uri);
    }
}
