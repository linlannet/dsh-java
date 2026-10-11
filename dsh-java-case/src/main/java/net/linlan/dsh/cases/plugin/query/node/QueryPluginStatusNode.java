package net.linlan.dsh.cases.plugin.query.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.QueryPluginStatusResponseDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 插件运行状态查询节点。
 * <p>流程：校验 pluginId → 调用插件运行时领域服务查询 → 映射为状态 DTO。</p>
 * <p>数据示例：传入 {@code pluginId=weather-forecast}；
 * 返回 {@code running=true, processId=8102, runtimeCommand="python main.py"}。</p>
 */
@Service
public class QueryPluginStatusNode extends AbstractStrategyRouter<String, NoContext, QueryPluginStatusResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(QueryPluginStatusNode.class);

    private final IPluginRuntimeService pluginRuntimeService;

    public QueryPluginStatusNode(IPluginRuntimeService pluginRuntimeService) {
        this.pluginRuntimeService = pluginRuntimeService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected QueryPluginStatusResponseDTO doApply(String pluginId, NoContext context) {
        CaseGuard.requireText(pluginId, "pluginId must not be blank");
        var result = pluginRuntimeService.status(pluginId);
        log.debug("[Plugin] 插件状态查询完成，pluginId={} running={}", result.pluginId(), result.running());
        return new QueryPluginStatusResponseDTO(
                result.pluginId(),
                result.processId(),
                result.running(),
                result.runtimeCommand(),
                result.workingDirectory(),
                result.startedAt(),
                result.message()
        );
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<String, NoContext, QueryPluginStatusResponseDTO> getNext(String pluginId, NoContext context) {
        return defaultStrategyHandler;
    }
}
