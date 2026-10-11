package net.linlan.dsh.cases.plugin.lifecycle.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeCommand;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeContext;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeOutcome;
import net.linlan.dsh.domain.plugin.IPluginBridgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 插件桥接检查节点。
 * <p>流程：记录检查时间 → 调用插件桥接领域服务生成计划 → 写入上下文 → 转到桥接绑定节点。</p>
 * <p>数据示例：检查 {@code weather-forecast} 后上下文得到
 * {@code bridgePlan=PluginBridgePlanVO(bridgeReady=true, runtimeCommand="python main.py")}。</p>
 */
@Service
public class InspectBridgeNode
        extends AbstractStrategyRouter<PluginRuntimeCommand, PluginRuntimeContext, PluginRuntimeOutcome> {

    private static final Logger log = LoggerFactory.getLogger(InspectBridgeNode.class);

    private final IPluginBridgeService pluginBridgeService;
    private final BindBridgeNode bindBridgeNode;

    public InspectBridgeNode(
            IPluginBridgeService pluginBridgeService,
            BindBridgeNode bindBridgeNode
    ) {
        this.pluginBridgeService = pluginBridgeService;
        this.bindBridgeNode = bindBridgeNode;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected PluginRuntimeOutcome doApply(
            PluginRuntimeCommand request,
            PluginRuntimeContext context
    ) {
        context.setCheckedAt(java.time.Instant.now());
        context.setBridgePlan(pluginBridgeService.inspect(context.getPlugin()));
        log.debug("[Plugin] 插件桥接检查完成，pluginId={} ready={}",
                context.getPlugin().getPluginId(), context.getBridgePlan().bridgeReady());
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
        return bindBridgeNode;
    }
}
