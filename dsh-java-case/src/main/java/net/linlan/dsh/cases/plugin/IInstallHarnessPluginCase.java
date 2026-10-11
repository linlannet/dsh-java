package net.linlan.dsh.cases.plugin;

import net.linlan.dsh.api.dto.InstallPluginRequestDTO;
import net.linlan.dsh.api.dto.InstallPluginResponseDTO;

/**
 * 编排插件安装用例。
 *
 * <p>调用流程：Controller/API → 插件用例 → 领域插件服务 → PluginRuntime/Bridge。</p>
 * <p>示例：安装插件后先激活，再通过统一工具名调用插件工具。</p>
 *
 */
public interface IInstallHarnessPluginCase {

    /**
     * 执行一次插件安装请求。
     *
     * @param request 安装请求
     * @return 安装结果
     */
    InstallPluginResponseDTO execute(InstallPluginRequestDTO request);
}
