package net.linlan.dsh.cases.plugin;

import net.linlan.dsh.api.dto.QueryPluginStatusResponseDTO;

/**
 * 编排插件进程状态查询。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
public interface IQueryHarnessPluginStatusCase {

    QueryPluginStatusResponseDTO execute(String pluginId);
}
