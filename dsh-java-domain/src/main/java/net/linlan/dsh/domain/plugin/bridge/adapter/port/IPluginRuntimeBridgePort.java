package net.linlan.dsh.domain.plugin.bridge.adapter.port;

import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;

/**
 * Resolves bridge execution details for one installed plugin.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public interface IPluginRuntimeBridgePort {

    PluginBridgePlanVO inspect(HarnessPluginEntity plugin);
}
