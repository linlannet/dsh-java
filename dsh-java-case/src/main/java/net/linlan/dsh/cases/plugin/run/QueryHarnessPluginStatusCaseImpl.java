package net.linlan.dsh.cases.plugin.run;

import net.linlan.dsh.api.dto.QueryPluginStatusResponseDTO;
import net.linlan.dsh.cases.plugin.IQueryHarnessPluginStatusCase;
import net.linlan.dsh.cases.plugin.query.factory.PluginQueryPipelineFactory;
import org.springframework.stereotype.Service;

/**
 * 协调插件进程状态查询。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
@Service
public class QueryHarnessPluginStatusCaseImpl implements IQueryHarnessPluginStatusCase {

    private final PluginQueryPipelineFactory pluginQueryPipelineFactory;

    /**
     * 查询Harness Plugin Status Case Impl。
     */
    public QueryHarnessPluginStatusCaseImpl(PluginQueryPipelineFactory pluginQueryPipelineFactory) {
        this.pluginQueryPipelineFactory = pluginQueryPipelineFactory;
    }

    /**
     * 用例执行入口，组装 Pipeline 并返回处理结果。
     */
    @Override
    public QueryPluginStatusResponseDTO execute(String pluginId) {
        return pluginQueryPipelineFactory.statusPipeline().execute(pluginId);
    }
}
