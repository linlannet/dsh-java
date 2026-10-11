package net.linlan.dsh.cases.plugin.manage.node;

import net.linlan.dsh.api.dto.PluginLifecycleResponseDTO;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 插件生命周期管理节点。
 * <p>流程：按 action 分派 → ENABLE/UNINSTALL/STOP 会先处理运行态 → 更新注册状态 → 返回生命周期 DTO。</p>
 * <p>数据示例：{@code action=UNINSTALL, pluginId=weather-forecast} 先停止进程，再返回 {@code status=UNINSTALLED}；
 * {@code action=STOP} 返回 {@code status=STOPPED}。</p>
 */
@Service
public class ManagePluginNode
        extends AbstractStrategyRouter<ManagePluginNode.Input, NoContext, PluginLifecycleResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(ManagePluginNode.class);

    private final IPluginRegistryService pluginRegistryService;
    private final IPluginRuntimeService pluginRuntimeService;

    public ManagePluginNode(
            IPluginRegistryService pluginRegistryService,
            IPluginRuntimeService pluginRuntimeService
    ) {
        this.pluginRegistryService = pluginRegistryService;
        this.pluginRuntimeService = pluginRuntimeService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected PluginLifecycleResponseDTO doApply(Input input, NoContext context) {
        CaseGuard.requireText(input.pluginId(), "pluginId must not be blank");
        log.info("[Plugin] 收到插件生命周期操作，pluginId={} action={}", input.pluginId(), input.action());
        return switch (input.action()) {
            case DISABLE -> disable(input.pluginId());
            case ENABLE -> enable(input.pluginId());
            case UNINSTALL -> uninstall(input.pluginId());
            case STOP -> stop(input.pluginId());
        };
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Input, NoContext, PluginLifecycleResponseDTO> getNext(Input input, NoContext context) {
        return defaultStrategyHandler;
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private PluginLifecycleResponseDTO disable(String pluginId) {
        pluginRuntimeService.stop(pluginId);
        var status = pluginRegistryService.disablePlugin(pluginId);
        return new PluginLifecycleResponseDTO(pluginId, status.name(), "plugin disabled", Instant.now());
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private PluginLifecycleResponseDTO enable(String pluginId) {
        var status = pluginRegistryService.enablePlugin(pluginId);
        return new PluginLifecycleResponseDTO(pluginId, status.name(), "plugin enabled", Instant.now());
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private PluginLifecycleResponseDTO uninstall(String pluginId) {
        pluginRuntimeService.stop(pluginId);
        var status = pluginRegistryService.uninstallPlugin(pluginId);
        return new PluginLifecycleResponseDTO(pluginId, status.name(), "plugin uninstalled", Instant.now());
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private PluginLifecycleResponseDTO stop(String pluginId) {
        var result = pluginRuntimeService.stop(pluginId);
        return new PluginLifecycleResponseDTO(
                pluginId,
                result.running() ? "RUNNING" : "STOPPED",
                result.message(),
                Instant.now()
        );
    }

    public enum Action {
        DISABLE,
        ENABLE,
        UNINSTALL,
        STOP
    }

    public record Input(Action action, String pluginId) {
    }
}
