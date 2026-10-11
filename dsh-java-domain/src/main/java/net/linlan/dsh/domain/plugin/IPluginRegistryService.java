package net.linlan.dsh.domain.plugin;

import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import java.util.Optional;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallationResultVO;

/**
 * Owns plugin registration and plugin lifecycle status persistence.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public interface IPluginRegistryService {

    PluginInstallationResultVO installPlugin(
            String pluginId,
            String displayName,
            String pluginVersion,
            String runtimeType,
            String sourcePath,
            String entrypoint
    );

    HarnessPluginEntity loadPlugin(String pluginId);

    PluginInstallStatusEnumVO refreshStatus(HarnessPluginEntity plugin, boolean bridgeReady);

    /**
     * 处理disablePlugin。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    PluginInstallStatusEnumVO disablePlugin(String pluginId);

    /**
     * 处理enablePlugin。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    PluginInstallStatusEnumVO enablePlugin(String pluginId);

    /**
     * 处理uninstallPlugin。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    PluginInstallStatusEnumVO uninstallPlugin(String pluginId);

    /**
     * 处理loadPluginOptional。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    Optional<HarnessPluginEntity> loadPluginOptional(String pluginId);
}
