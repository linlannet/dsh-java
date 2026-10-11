package net.linlan.dsh.cases.plugin.query.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 已安装插件列表节点。
 * <p>流程：接收空请求 → 读取插件仓储全部记录 → 映射为安装插件 DTO 列表。</p>
 * <p>数据示例：无入参；返回 {@code [{pluginId: "weather-forecast", sourcePath: "/plugins/weather", status: "ENABLED"}]}。</p>
 */
@Service
public class ListInstalledPluginsNode
        extends AbstractStrategyRouter<Void, NoContext, List<InstalledPluginResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(ListInstalledPluginsNode.class);

    private final IHarnessPluginRepository pluginRepository;

    public ListInstalledPluginsNode(IHarnessPluginRepository pluginRepository) {
        this.pluginRepository = pluginRepository;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<InstalledPluginResponseDTO> doApply(Void request, NoContext context) {
        var result = pluginRepository.listAll().stream().map(plugin -> new InstalledPluginResponseDTO(
                plugin.getPluginId(), plugin.getDisplayName(), plugin.getPluginVersion(),
                plugin.getRuntimeType().name(), plugin.getInstallMode().name(), plugin.getStatus().name(),
                plugin.getSourcePath(), plugin.getEntrypoint(), plugin.getInstalledAt()
        )).toList();
        log.debug("[Plugin] 已安装插件查询完成，count={}", result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Void, NoContext, List<InstalledPluginResponseDTO>> getNext(Void request, NoContext context) {
        return defaultStrategyHandler;
    }
}
