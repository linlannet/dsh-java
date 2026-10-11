package net.linlan.dsh.domain.plugin.bridge.adapter.repository;

import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import java.time.Instant;

/**
 * Persists the last bridge-resolution result for one plugin.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public interface IHarnessPluginRuntimeBindingRepository {

    void save(String pluginId, PluginBridgePlanVO plan, Instant checkedAt);
}
