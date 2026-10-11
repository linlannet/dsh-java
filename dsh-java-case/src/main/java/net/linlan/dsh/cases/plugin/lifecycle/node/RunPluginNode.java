package net.linlan.dsh.cases.plugin.lifecycle.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeCommand;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeContext;
import net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeOutcome;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 插件运行终点节点。
 * <p>流程：校验桥接已就绪 → 调用插件运行时领域服务启动进程 → 将运行结果包装为 Run 多态结果。</p>
 * <p>数据示例：返回 {@code Run(result.pluginId=weather-forecast, processId=8102, running=true)}。</p>
 */
@Service
public class RunPluginNode
        extends AbstractStrategyRouter<PluginRuntimeCommand, PluginRuntimeContext, PluginRuntimeOutcome> {

    private static final Logger log = LoggerFactory.getLogger(RunPluginNode.class);

    private final IPluginRuntimeService pluginRuntimeService;

    public RunPluginNode(IPluginRuntimeService pluginRuntimeService) {
        this.pluginRuntimeService = pluginRuntimeService;
    }

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
        var result = pluginRuntimeService.start(context.getPlugin(), plan);
        var outcome = new PluginRuntimeOutcome.Run(new net.linlan.dsh.api.dto.RunPluginResponseDTO(
                result.pluginId(),
                result.processId(),
                result.runtimeCommand(),
                result.workingDirectory(),
                result.running(),
                result.startedAt(),
                result.message()
        ));
        log.info("[Plugin] 插件运行启动完成，pluginId={} processId={} running={}",
                result.pluginId(), result.processId(), result.running());
        return outcome;
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
