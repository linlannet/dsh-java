package net.linlan.dsh.domain.plugin;

import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import java.time.Instant;

/**
 * Owns plugin bridge inspection and binding persistence.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public interface IPluginBridgeService {

    PluginBridgePlanVO inspect(HarnessPluginEntity plugin);

    void recordBinding(String pluginId, PluginBridgePlanVO plan, Instant checkedAt);
}
