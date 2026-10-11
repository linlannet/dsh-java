package net.linlan.dsh.trigger.service;

import net.linlan.dsh.cases.plugin.IActivateHarnessPluginCase;
import net.linlan.dsh.cases.plugin.IInstallHarnessPluginCase;
import net.linlan.dsh.cases.plugin.IManageHarnessPluginCase;
import net.linlan.dsh.cases.plugin.IRunHarnessPluginCase;
import net.linlan.dsh.api.dto.ActivatePluginRequestDTO;
import net.linlan.dsh.api.dto.ActivatePluginResponseDTO;
import net.linlan.dsh.api.dto.InstallPluginRequestDTO;
import net.linlan.dsh.api.dto.InstallPluginResponseDTO;
import net.linlan.dsh.api.dto.PluginLifecycleResponseDTO;
import net.linlan.dsh.api.dto.RunPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginResponseDTO;
import net.linlan.dsh.api.IHarnessPluginCommandApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.stereotype.Service;

/**
 * 插件命令适配器。
 * <p>
 * 执行流程：
 * 1. 接收 Trigger 层传入的插件命令请求（api DTO）；
 * 2. 直接转交对应的用例层执行；
 * 3. 用例返回 API Response DTO。
 *
 * <p>调用流程：HTTP/Web 层 → API 适配器 → 应用门面。</p>
 * <p>示例：控制器只负责协议转换，不写业务规则。</p>
 *
 */
@Service
public class HarnessPluginCommandApi implements IHarnessPluginCommandApi {

    private final IInstallHarnessPluginCase installHarnessPluginCase;
    private final IActivateHarnessPluginCase activateHarnessPluginCase;
    private final IRunHarnessPluginCase runHarnessPluginCase;
    private final IManageHarnessPluginCase manageHarnessPluginCase;

    /**
     * 初始化插件命令适配器，并注入安装、激活、运行和生命周期用例。
     * <p>流程：HTTP 请求 → API 适配器 → 用例层。</p>
     */
    public HarnessPluginCommandApi(
            IInstallHarnessPluginCase installHarnessPluginCase,
            IActivateHarnessPluginCase activateHarnessPluginCase,
            IRunHarnessPluginCase runHarnessPluginCase,
            IManageHarnessPluginCase manageHarnessPluginCase
    ) {
        this.installHarnessPluginCase = installHarnessPluginCase;
        this.activateHarnessPluginCase = activateHarnessPluginCase;
        this.runHarnessPluginCase = runHarnessPluginCase;
        this.manageHarnessPluginCase = manageHarnessPluginCase;
    }

    /**
     * 安装组件，写入登记信息并返回安装结果。
     */
    public Response<InstallPluginResponseDTO> installPlugin(InstallPluginRequestDTO request) {
        return execute(() -> installHarnessPluginCase.execute(request));
    }

    /**
     * 激活组件，准备其运行时能力。
     */
    public Response<ActivatePluginResponseDTO> activatePlugin(ActivatePluginRequestDTO request) {
        return execute(() -> activateHarnessPluginCase.execute(request));
    }

    /**
     * 运行Plugin。
     */
    public Response<RunPluginResponseDTO> runPlugin(RunPluginRequestDTO request) {
        return execute(() -> runHarnessPluginCase.execute(request));
    }

    /**
     * 停用组件，但保留登记信息。
     */
    public Response<PluginLifecycleResponseDTO> disablePlugin(String pluginId) {
        return execute(() -> manageHarnessPluginCase.disable(pluginId));
    }

    /**
     * 启用已登记的组件。
     */
    public Response<PluginLifecycleResponseDTO> enablePlugin(String pluginId) {
        return execute(() -> manageHarnessPluginCase.enable(pluginId));
    }

    /**
     * 卸载组件并清理运行时资源。
     */
    public Response<PluginLifecycleResponseDTO> uninstallPlugin(String pluginId) {
        return execute(() -> manageHarnessPluginCase.uninstall(pluginId));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
