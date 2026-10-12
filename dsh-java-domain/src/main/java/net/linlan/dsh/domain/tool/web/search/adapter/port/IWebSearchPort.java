package net.linlan.dsh.domain.tool.web.search.adapter.port;

import net.linlan.dsh.domain.tool.web.search.model.valobj.SearchResult;
import java.util.List;

/**
 * IWebSearchPort 定义Web搜索的领域端口，由基础设施层适配。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IWebSearchPort {

    /**
     * 处理search。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param query query参数。
     * @param limit limit参数。
     * @return 领域处理结果。
     */
    List<SearchResult> search(String query, int limit);
}
