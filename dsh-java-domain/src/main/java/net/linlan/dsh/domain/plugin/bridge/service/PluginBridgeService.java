package net.linlan.dsh.domain.plugin.bridge.service;

import net.linlan.dsh.domain.plugin.IPluginBridgeService;
import net.linlan.dsh.domain.plugin.bridge.adapter.port.IPluginRuntimeBridgePort;
import net.linlan.dsh.domain.plugin.bridge.adapter.repository.IHarnessPluginRuntimeBindingRepository;
import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Owns plugin bridge inspection and binding persistence.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
@Service
public class PluginBridgeService implements IPluginBridgeService {

    private final IPluginRuntimeBridgePort pluginRuntimeBridgePort;
    private final IHarnessPluginRuntimeBindingRepository harnessPluginRuntimeBindingRepository;

    /**
     * 初始化插件桥接服务。
     * <p>流程：加载插件清单 → 绑定运行时 → 转换工具调用请求。</p>
     */
    public PluginBridgeService(
            IPluginRuntimeBridgePort pluginRuntimeBridgePort,
            IHarnessPluginRuntimeBindingRepository harnessPluginRuntimeBindingRepository
    ) {
        this.pluginRuntimeBridgePort = pluginRuntimeBridgePort;
        this.harnessPluginRuntimeBindingRepository = harnessPluginRuntimeBindingRepository;
    }

    @Override
    public PluginBridgePlanVO inspect(HarnessPluginEntity plugin) {
        return pluginRuntimeBridgePort.inspect(plugin);
    }
    /**
     * record Binding。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param pluginId 插件 ID。
     * @param plan plan参数。
     * @param checkedAt checked At参数。
     */


    @Override
    public void recordBinding(String pluginId, PluginBridgePlanVO plan, Instant checkedAt) {
        harnessPluginRuntimeBindingRepository.save(pluginId, plan, checkedAt);
    }
}
