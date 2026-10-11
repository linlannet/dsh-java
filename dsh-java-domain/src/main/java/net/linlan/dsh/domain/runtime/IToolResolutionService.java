package net.linlan.dsh.domain.runtime;

import java.util.List;

/**
 * 工具解析服务接口。
 * <p>
 * 执行流程：
 * 1. 接收用户请求的工具编码；
 * 2. 根据目录和策略进行过滤；
 * 3. 返回最终允许启用的工具列表。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IToolResolutionService {

    /**
     * 解析可启用工具列表。
     * <p>
     * 执行流程：
     * 1. 读取 requestedTools；
     * 2. 过滤掉不允许执行的工具；
     * 3. 返回最终可启用的工具编码。
     *
     * @param requestedTools 调用方请求的工具编码
     * @return 可启用的工具编码
     */
    List<String> resolveEnabledTools(List<String> requestedTools);
}
