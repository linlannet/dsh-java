package net.linlan.dsh.cases.plugin.lifecycle.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeCommand;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeContext;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeOutcome;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 插件解析节点。
 * <p>流程：校验 pluginId → 从插件注册领域服务加载实体 → 写入运行期上下文 → 转到桥接检查节点。</p>
 * <p>数据示例：传入 {@code pluginId=weather-forecast}；上下文得到 {@code plugin=HarnessPluginEntity(weather-forecast)}。</p>
 */
@Service
public class ResolvePluginNode
        extends AbstractStrategyRouter<PluginRuntimeCommand, PluginRuntimeContext, PluginRuntimeOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ResolvePluginNode.class);

    private final IPluginRegistryService pluginRegistryService;
    private final InspectBridgeNode inspectBridgeNode;

    public ResolvePluginNode(
            IPluginRegistryService pluginRegistryService,
            InspectBridgeNode inspectBridgeNode
    ) {
        this.pluginRegistryService = pluginRegistryService;
        this.inspectBridgeNode = inspectBridgeNode;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected PluginRuntimeOutcome doApply(
            PluginRuntimeCommand request,
            PluginRuntimeContext context
    ) {
        CaseGuard.requireText(request.pluginId(), "pluginId must not be blank");
        context.setPlugin(pluginRegistryService.loadPlugin(request.pluginId()));
        log.debug("[Plugin] 插件解析完成，pluginId={}", request.pluginId());
        return null;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<PluginRuntimeCommand, PluginRuntimeContext, PluginRuntimeOutcome> getNext(
            PluginRuntimeCommand request,
            PluginRuntimeContext context
    ) {
        return inspectBridgeNode;
    }
}
