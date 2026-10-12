package net.linlan.dsh.domain.tool.mcp.resource.adapter.port;

import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import java.util.List;

/**
 * IMcpResourcePort 定义MCP资源发现的领域端口，由基础设施层适配。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IMcpResourcePort {

    /**
     * 处理list Resources。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param scope scope参数。
     * @return 领域处理结果。
     */
    List<McpResource> listResources(String scope);

    /**
     * 处理read Resource。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param uri uri参数。
     * @return 领域处理结果。
     */
    String readResource(String uri);
}
