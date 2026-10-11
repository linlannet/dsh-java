package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.domain.plugin.runtime.adapter.port.IPluginProcessPort;
import net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginProcessStatusVO;
import net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginRunResultVO;
import net.linlan.dsh.domain.tool.plugin.PluginToolBridgeService;
import net.linlan.dsh.infrastructure.adapter.plugin.java.JavaPluginRuntimeManager;
import net.linlan.dsh.infrastructure.gateway.PluginSidecarGatewayService;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Adapts plugin process lifecycle calls to either the Java in-process runtime
 * (for JAVA_NATIVE plugins) or the Node sidecar gateway (for DSH_NODE_BRIDGE).
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Component
public class PluginProcessPort implements IPluginProcessPort {

    private final PluginSidecarGatewayService pluginSidecarGatewayService;
    private final JavaPluginRuntimeManager javaPluginRuntimeManager;
    private final PluginToolBridgeService pluginToolBridgeService;

    /**
     * 初始化插件进程端口。
     * <p>流程：定位插件入口 → 启动/停止子进程 → 回传状态。</p>
     */
    public PluginProcessPort(
            PluginSidecarGatewayService pluginSidecarGatewayService,
            JavaPluginRuntimeManager javaPluginRuntimeManager,
            PluginToolBridgeService pluginToolBridgeService
    ) {
        this.pluginSidecarGatewayService = pluginSidecarGatewayService;
        this.javaPluginRuntimeManager = javaPluginRuntimeManager;
        this.pluginToolBridgeService = pluginToolBridgeService;
    }

    @Override
    public PluginRunResultVO start(HarnessPluginEntity plugin, PluginBridgePlanVO plan) {
        if (plugin.getRuntimeType() == PluginRuntimeTypeEnumVO.JAVA_NATIVE) {
            return startJavaPlugin(plugin, plan);
        }
        PluginRunResultVO result = pluginSidecarGatewayService.start(
                plugin.getPluginId(), plan.workingDirectory(), plan.entrypointPath());
        try {
            pluginSidecarGatewayService.runningProcess(plugin.getPluginId())
                    .ifPresent(process -> pluginToolBridgeService.bridgePlugin(plugin.getPluginId(), process));
        } catch (RuntimeException exception) {
            pluginSidecarGatewayService.stop(plugin.getPluginId());
            throw exception;
        }
        return result;
    }
    /**
     * 返回 status。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public PluginProcessStatusVO status(String pluginId) {
        JavaPluginRuntimeManager.JavaPluginStatusResult javaStatus = javaPluginRuntimeManager.status(pluginId);
        if (javaStatus.running()) {
            return new PluginProcessStatusVO(pluginId, 0L, true, "java-in-process",
                    null, Instant.now(), javaStatus.message());
        }
        PluginProcessStatusVO nodeStatus = pluginSidecarGatewayService.status(pluginId);
        if (nodeStatus.running()) return nodeStatus;
        return new PluginProcessStatusVO(pluginId, null, false, null, null, null, javaStatus.message());
    }
    /**
     * 停止目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public PluginProcessStatusVO stop(String pluginId) {
        pluginToolBridgeService.unbridgePlugin(pluginId);
        JavaPluginRuntimeManager.JavaPluginStatusResult javaResult = javaPluginRuntimeManager.stop(pluginId);
        if (!javaResult.running()) {
            return new PluginProcessStatusVO(pluginId, null, false, "java-in-process",
                    null, Instant.now(), javaResult.message());
        }
        return pluginSidecarGatewayService.stop(pluginId);
    }

    /**
     * 启动 Java 插件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param plugin 插件参数。
     * @param plan 计划参数。
     * @return 方法执行结果。
     */
    private PluginRunResultVO startJavaPlugin(HarnessPluginEntity plugin, PluginBridgePlanVO plan) {
        Path jarPath = Path.of(plan.entrypointPath());
        JavaPluginRuntimeManager.JavaPluginStartResult result =
                javaPluginRuntimeManager.start(plugin.getPluginId(), jarPath);
        return new PluginRunResultVO(
                result.pluginId(),
                0L,
                "java-in-process",
                null,
                result.running(),
                result.startedAt(),
                result.message()
        );
    }
}
