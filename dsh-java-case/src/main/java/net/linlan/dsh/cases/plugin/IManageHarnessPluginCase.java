package net.linlan.dsh.cases.plugin;

import net.linlan.dsh.api.dto.PluginLifecycleResponseDTO;

/**
 * 编排插件生命周期管理，包括启用、停用、卸载和停止。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
public interface IManageHarnessPluginCase {

    PluginLifecycleResponseDTO disable(String pluginId);

    PluginLifecycleResponseDTO enable(String pluginId);

    PluginLifecycleResponseDTO uninstall(String pluginId);

    PluginLifecycleResponseDTO stop(String pluginId);
}
