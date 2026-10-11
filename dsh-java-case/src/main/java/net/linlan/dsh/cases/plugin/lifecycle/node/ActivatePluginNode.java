package net.linlan.dsh.cases.plugin.lifecycle.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeCommand;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeContext;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeOutcome;
import net.linlan.dsh.api.dto.ActivatePluginResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 插件激活终点节点。
 * <p>流程：校验桥接已就绪 → 组合插件、状态和桥接计划 → 返回激活多态结果。</p>
 * <p>数据示例：返回 {@code Activation(result.pluginId=weather-forecast, status=ENABLED, bridgeReady=true)}。</p>
 */
@Service
public class ActivatePluginNode
        extends AbstractStrategyRouter<PluginRuntimeCommand, PluginRuntimeContext, PluginRuntimeOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ActivatePluginNode.class);

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected PluginRuntimeOutcome doApply(
            PluginRuntimeCommand request,
            PluginRuntimeContext context
    ) {
        var plan = context.getBridgePlan();
        if (!plan.bridgeReady()) {
            throw new IllegalStateException("plugin bridge is not ready: " + plan.message());
        }
        var result = new PluginRuntimeOutcome.Activation(new ActivatePluginResponseDTO(
                context.getPlugin().getPluginId(),
                context.getStatus().name(),
                plan.packageType().name(),
                plan.runtimeCommand(),
                plan.bridgeReady(),
                plan.message(),
                context.getCheckedAt()
        ));
        log.info("[Plugin] 插件激活完成，pluginId={} status={} bridgeReady={}",
                context.getPlugin().getPluginId(), context.getStatus(), plan.bridgeReady());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<PluginRuntimeCommand, PluginRuntimeContext, PluginRuntimeOutcome> getNext(
            PluginRuntimeCommand request,
            PluginRuntimeContext context
    ) {
        return defaultStrategyHandler;
    }
}
