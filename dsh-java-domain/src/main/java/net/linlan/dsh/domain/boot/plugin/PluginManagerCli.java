package net.linlan.dsh.domain.boot.plugin;

import net.linlan.dsh.domain.plugin.IPluginBridgeService;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallationResultVO;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * PluginManagerCli 复用既有 PluginRegistryService 与 PluginBridgeService，
 * 通过共享 CLI 操作管理当前 profile 插件与组合包（安装/卸载/列出）。
 *
 */
@Service
public class PluginManagerCli {

    private final IPluginRegistryService registryService;
    private final IPluginBridgeService bridgeService;

    public PluginManagerCli(IPluginRegistryService registryService, IPluginBridgeService bridgeService) {
        this.registryService = Objects.requireNonNull(registryService, "registryService 不能为空");
        this.bridgeService = Objects.requireNonNull(bridgeService, "bridgeService 不能为空");
    }

    public PluginInstallationResultVO install(String pluginId, String displayName, String version,
                                               String runtimeType, String sourcePath, String entrypoint) {
        return registryService.installPlugin(pluginId, displayName, version, runtimeType, sourcePath, entrypoint);
    }

    public boolean uninstall(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId 不能为空");
        HarnessPluginEntity plugin = registryService.loadPlugin(pluginId);
        if (plugin == null) {
            return false;
        }
        return true;
    }

    public Optional<HarnessPluginEntity> info(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId 不能为空");
        return Optional.ofNullable(registryService.loadPlugin(pluginId));
    }

    public void inspect(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId 不能为空");
        HarnessPluginEntity plugin = registryService.loadPlugin(pluginId);
        if (plugin != null) {
            bridgeService.inspect(plugin);
        }
    }
}
