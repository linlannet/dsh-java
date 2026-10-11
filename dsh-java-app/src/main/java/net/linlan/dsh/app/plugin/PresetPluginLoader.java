package net.linlan.dsh.app.plugin;

import net.linlan.dsh.cases.plugin.IInstallHarnessPluginCase;
import net.linlan.dsh.cases.plugin.IRunHarnessPluginCase;
import net.linlan.dsh.api.dto.InstallPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginResponseDTO;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 启动时安装 application.yml 中尚未注册的预置插件；
 * 如果请求 auto-enable，还会重新启用处于 DISABLED 状态的预置插件。
 *
 * <p>调用流程：Spring Boot 启动 → 加载模型/插件预设 → 注册运行时组件。</p>
 * <p>示例：启动器同步默认模型并装载预置插件。</p>
 *
 */
@Configuration
@Order(20)
public class PresetPluginLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PresetPluginLoader.class);

    private final HarnessExtensionsProperties properties;
    private final IInstallHarnessPluginCase installCase;
    private final IRunHarnessPluginCase runCase;
    private final IPluginRegistryService pluginRegistryService;
    private final IHarnessPluginRepository pluginRepository;
    private final IPluginRuntimeService pluginRuntimeService;

    /**
     * 初始化预置插件加载器。
     * <p>流程：读取 application.yml 中的预置插件配置 → 调用插件注册服务安装/激活。</p>
     */
    public PresetPluginLoader(
            HarnessExtensionsProperties properties,
            IInstallHarnessPluginCase installCase,
            IRunHarnessPluginCase runCase,
            IPluginRegistryService pluginRegistryService,
            IHarnessPluginRepository pluginRepository,
            IPluginRuntimeService pluginRuntimeService
    ) {
        this.properties = properties;
        this.installCase = installCase;
        this.runCase = runCase;
        this.pluginRegistryService = pluginRegistryService;
        this.pluginRepository = pluginRepository;
        this.pluginRuntimeService = pluginRuntimeService;
    }

    /**
     * 运行。
     */
    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        Set<String> presetPluginIds = properties.getPlugins().getPreset() == null
                ? Set.of()
                : properties.getPlugins().getPreset().stream()
                        .map(HarnessExtensionsProperties.PresetPlugin::getPluginId)
                        .collect(java.util.stream.Collectors.toSet());
        reconcilePersistedPlugins(presetPluginIds);
        if (properties.getPlugins().getPreset() == null || properties.getPlugins().getPreset().isEmpty()) {
            return;
        }
        for (HarnessExtensionsProperties.PresetPlugin preset : properties.getPlugins().getPreset()) {
            try {
                installPreset(preset);
            } catch (Exception | LinkageError e) {
                log.warn("Preset plugin '{}' install skipped: {}", preset.getPluginId(), e.getMessage());
            }
        }
    }

    /**
     * 安装组件，写入登记信息并返回安装结果。
     */
    private void installPreset(HarnessExtensionsProperties.PresetPlugin preset) {
        Optional<HarnessPluginEntity> existing = pluginRegistryService.loadPluginOptional(preset.getPluginId());
        if (existing.isPresent()) {
            HarnessPluginEntity plugin = existing.get();
            if (preset.isAutoStart() && plugin.getStatus() == PluginInstallStatusEnumVO.DISABLED) {
                pluginRegistryService.enablePlugin(preset.getPluginId());
                log.info("Preset plugin '{}' re-enabled", preset.getPluginId());
            }
        }
        else {
            InstallPluginRequestDTO request = new InstallPluginRequestDTO(
                    preset.getPluginId(),
                    preset.getDisplayName(),
                    preset.getPluginVersion(),
                    preset.getRuntimeType(),
                    preset.getSourcePath(),
                    preset.getEntrypoint()
            );
            installCase.execute(request);
            log.info("Preset plugin '{}' installed from {}", preset.getPluginId(), preset.getSourcePath());
        }

        if (!preset.isAutoStart()) {
            return;
        }
        RunPluginResponseDTO result = runCase.execute(new RunPluginRequestDTO(preset.getPluginId()));
        if (result.running()) {
            log.info("Preset plugin '{}' restored: {}", preset.getPluginId(), result.message());
        } else {
            log.warn("Preset plugin '{}' failed to restore: {}", preset.getPluginId(), result.message());
        }
    }

    /**
     * 对账插件登记状态和运行时状态。
     * <p>插件登记状态与进程内运行状态是两个概念；宿主重启后进程状态自然丢失。
     * 运行时状态优先于登记状态：已运行但登记为 FAILED 的插件会被纠正；已登记 ACTIVE
     * 但运行时缺失的插件会被重新拉起，保证重启后仍会注册工具和提示词。
     *
     * @param presetPluginIds 预置插件 ID，这些插件由下方 preset 流程处理。
     */
    private void reconcilePersistedPlugins(Set<String> presetPluginIds) {
        pluginRepository.listAll().stream()
                .filter(plugin -> !presetPluginIds.contains(plugin.getPluginId()))
                .forEach(plugin -> {
                    try {
                        if (pluginRuntimeService.status(plugin.getPluginId()).running()) {
                            if (plugin.getStatus() != PluginInstallStatusEnumVO.ACTIVE) {
                                plugin.activate();
                                pluginRepository.save(plugin);
                                log.info("Plugin '{}' reconciled to ACTIVE from runtime state",
                                        plugin.getPluginId());
                            }
                            return;
                        }
                        if (plugin.getStatus() == PluginInstallStatusEnumVO.DISABLED
                                || plugin.getStatus() == PluginInstallStatusEnumVO.UNINSTALLED) {
                            return;
                        }
                        RunPluginResponseDTO result = runCase.execute(
                                new RunPluginRequestDTO(plugin.getPluginId()));
                        if (result.running()) {
                            log.info("Active plugin '{}' restored: {}",
                                    plugin.getPluginId(), result.message());
                        } else {
                            log.warn("Active plugin '{}' failed to restore: {}",
                                    plugin.getPluginId(), result.message());
                        }
                    } catch (Exception | LinkageError e) {
                        log.warn("Active plugin '{}' restore skipped: {}",
                                plugin.getPluginId(), e.getMessage());
                    }
                });
    }
}
