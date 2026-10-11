package net.linlan.dsh.cases.plugin;

import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;

import net.linlan.dsh.cases.plugin.query.factory.PluginQueryPipelineFactory;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 已安装插件查询编排实现。
 * <p>流程：无入参 → 创建查询 Pipeline → ListInstalledPluginsNode 读取仓储 → 转为 DTO 列表。</p>
 * <p>数据示例：无入参；返回 {@code [{pluginId: "weather-forecast", displayName: "Weather Forecast", status: "ENABLED"}]}。</p>
 */
@Service
public class InstalledPluginQueryCaseImpl implements IInstalledPluginQueryCase {
    private final PluginQueryPipelineFactory pluginQueryPipelineFactory;

    public InstalledPluginQueryCaseImpl(PluginQueryPipelineFactory pluginQueryPipelineFactory) {
        this.pluginQueryPipelineFactory = pluginQueryPipelineFactory;
    }

    /**
     * 编排已安装插件查询，执行安装清单 Pipeline。
     */
    @Override
    public List<InstalledPluginResponseDTO> listInstalledPlugins() {
        return pluginQueryPipelineFactory.installedPipeline().execute(null);
    }
}
