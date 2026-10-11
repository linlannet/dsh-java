package net.linlan.dsh.domain.runtime.tool.service;

import net.linlan.dsh.domain.runtime.IToolResolutionService;
import net.linlan.dsh.domain.runtime.tool.adapter.port.IToolCatalogPort;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 工具解析服务。
 * <p>
 * 执行流程：
 * 1. 接收用户请求的工具集合；
 * 2. 调用工具目录端口完成过滤；
 * 3. 返回最终可启用的工具列表。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class ToolResolutionService implements IToolResolutionService {

    private final IToolCatalogPort toolCatalogPort;

    /**
     * 转换为 ol Resolution Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param toolCatalogPort 工具 Catalog Port参数。
     * @return 方法执行结果。
     */
    public ToolResolutionService(IToolCatalogPort toolCatalogPort) {
        this.toolCatalogPort = toolCatalogPort;
    }

    /**
     * 解析可启用工具。
     * <p>
     * 执行流程：
     * 1. 读取请求工具列表；
     * 2. 调用工具目录端口过滤；
     * 3. 返回过滤后的工具列表。
     */
    @Override
    public List<String> resolveEnabledTools(List<String> requestedTools) {
        return toolCatalogPort.resolveEnabledTools(requestedTools);
    }
}
