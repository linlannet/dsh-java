package net.linlan.dsh.app.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.linlan.dsh.api.dto.InstallPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginResponseDTO;
import net.linlan.dsh.cases.plugin.IInstallHarnessPluginCase;
import net.linlan.dsh.cases.plugin.IRunHarnessPluginCase;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallationResultVO;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PresetPluginLoaderTest {

    @Test
    void restoresPersistedActivePluginAfterHostRestart() {
        HarnessPluginEntity activePlugin = new HarnessPluginEntity(
                "dsh-java-mall-plugin",
                "DSH Java Mall App",
                "1.0.0-SNAPSHOT",
                PluginRuntimeTypeEnumVO.JAVA_NATIVE,
                PluginInstallModeEnumVO.NATIVE,
                "/plugins/dsh-java-mall-plugin.jar",
                "dsh-java-mall-plugin.jar",
                PluginInstallStatusEnumVO.ACTIVE,
                Instant.now()
        );
        List<HarnessPluginEntity> plugins = List.of(activePlugin);
        List<String> runPluginIds = new ArrayList<>();
        Fixture fixture = new Fixture(plugins, runPluginIds);
        PresetPluginLoader loader = new PresetPluginLoader(
                fixture.properties,
                fixture.installCase,
                fixture.runCase,
                fixture.registryService,
                fixture.repository,
                fixture.runtimeService
        );

        loader.run(null);

        assertEquals(List.of("dsh-java-mall-plugin"), runPluginIds);
    }

    @Test
    void doesNotRestoreActivePresetTwice() {
        HarnessPluginEntity activePreset = new HarnessPluginEntity(
                "sample-tools",
                "Sample Tools Plugin",
                "1.0.0",
                PluginRuntimeTypeEnumVO.JAVA_NATIVE,
                PluginInstallModeEnumVO.NATIVE,
                "/plugins/sample-tools.jar",
                "sample-tools.jar",
                PluginInstallStatusEnumVO.ACTIVE,
                Instant.now()
        );
        List<String> runPluginIds = new ArrayList<>();
        Fixture fixture = new Fixture(List.of(activePreset), runPluginIds);
        fixture.properties.getPlugins().setPreset(List.of(preset("sample-tools")));
        PresetPluginLoader loader = new PresetPluginLoader(
                fixture.properties,
                fixture.installCase,
                fixture.runCase,
                fixture.registryService,
                fixture.repository,
                fixture.runtimeService
        );

        loader.run(null);

        assertEquals(List.of("sample-tools"), runPluginIds);
    }

    @Test
    void reconcilesFailedRegistryStatusFromRunningPlugin() {
        HarnessPluginEntity failedPlugin = new HarnessPluginEntity(
                "dsh-java-mall-plugin",
                "DSH Java Mall App",
                "1.0.0-SNAPSHOT",
                PluginRuntimeTypeEnumVO.JAVA_NATIVE,
                PluginInstallModeEnumVO.NATIVE,
                "/plugins/dsh-java-mall-plugin.jar",
                "dsh-java-mall-plugin.jar",
                PluginInstallStatusEnumVO.FAILED,
                Instant.now()
        );
        Fixture fixture = new Fixture(List.of(failedPlugin), new ArrayList<>());
        fixture.runtimeStatus.put("dsh-java-mall-plugin", true);
        PresetPluginLoader loader = new PresetPluginLoader(
                fixture.properties,
                fixture.installCase,
                fixture.runCase,
                fixture.registryService,
                fixture.repository,
                fixture.runtimeService
        );

        loader.run(null);

        assertEquals(
                PluginInstallStatusEnumVO.ACTIVE,
                fixture.repository.findByPluginId("dsh-java-mall-plugin").orElseThrow().getStatus());
    }

    @Test
    void retriesPersistedFailedPluginAfterHostRestart() {
        HarnessPluginEntity failedPlugin = new HarnessPluginEntity(
                "dsh-java-mall-plugin",
                "DSH Java Mall App",
                "1.0.0-SNAPSHOT",
                PluginRuntimeTypeEnumVO.JAVA_NATIVE,
                PluginInstallModeEnumVO.NATIVE,
                "/plugins/dsh-java-mall-plugin.jar",
                "dsh-java-mall-plugin.jar",
                PluginInstallStatusEnumVO.FAILED,
                Instant.now()
        );
        List<String> runPluginIds = new ArrayList<>();
        Fixture fixture = new Fixture(List.of(failedPlugin), runPluginIds);
        PresetPluginLoader loader = new PresetPluginLoader(
                fixture.properties,
                fixture.installCase,
                fixture.runCase,
                fixture.registryService,
                fixture.repository,
                fixture.runtimeService
        );

        loader.run(null);

        assertEquals(List.of("dsh-java-mall-plugin"), runPluginIds);
    }

    private HarnessExtensionsProperties.PresetPlugin preset(String pluginId) {
        HarnessExtensionsProperties.PresetPlugin preset = new HarnessExtensionsProperties.PresetPlugin();
        preset.setPluginId(pluginId);
        preset.setDisplayName(pluginId);
        preset.setPluginVersion("1.0.0");
        preset.setRuntimeType("JAVA_NATIVE");
        preset.setSourcePath("./plugins/" + pluginId + ".jar");
        preset.setEntrypoint(pluginId + ".jar");
        preset.setAutoStart(true);
        return preset;
    }

    private static final class Fixture {
        private final HarnessExtensionsProperties properties = new HarnessExtensionsProperties();
        private final Map<String, HarnessPluginEntity> pluginMap = new HashMap<>();
        private final List<String> runPluginIds;
        private final List<HarnessPluginEntity> plugins;
        private final IInstallHarnessPluginCase installCase;
        private final IRunHarnessPluginCase runCase;
        private final IPluginRegistryService registryService;
        private final IHarnessPluginRepository repository;
        private final Map<String, Boolean> runtimeStatus = new HashMap<>();
        private final IPluginRuntimeService runtimeService;

        private Fixture(List<HarnessPluginEntity> plugins, List<String> runPluginIds) {
            this.plugins = plugins;
            this.runPluginIds = runPluginIds;
            plugins.forEach(plugin -> pluginMap.put(plugin.getPluginId(), plugin));
            this.installCase = request -> {
                throw new AssertionError("presets should not be installed in this test");
            };
            this.runCase = request -> {
                runPluginIds.add(request.pluginId());
                return new RunPluginResponseDTO(
                        request.pluginId(), 0, "java", null, true, Instant.now(), "java plugin is running");
            };
            this.registryService = new IPluginRegistryService() {
                @Override public PluginInstallationResultVO installPlugin(
                        String pluginId, String displayName, String pluginVersion,
                        String runtimeType, String sourcePath, String entrypoint) {
                    throw new AssertionError("registry installation should not run in this test");
                }
                @Override public Optional<HarnessPluginEntity> loadPluginOptional(String pluginId) {
                    return Optional.ofNullable(pluginMap.get(pluginId));
                }
                @Override public HarnessPluginEntity loadPlugin(String pluginId) {
                    return pluginMap.get(pluginId);
                }
                @Override public PluginInstallStatusEnumVO refreshStatus(
                        HarnessPluginEntity plugin, boolean bridgeReady) {
                    return plugin.getStatus();
                }
                @Override public PluginInstallStatusEnumVO disablePlugin(String pluginId) {
                    return PluginInstallStatusEnumVO.DISABLED;
                }
                @Override public PluginInstallStatusEnumVO enablePlugin(String pluginId) {
                    return PluginInstallStatusEnumVO.ACTIVE;
                }
                @Override public PluginInstallStatusEnumVO uninstallPlugin(String pluginId) {
                    return PluginInstallStatusEnumVO.UNINSTALLED;
                }
            };
            this.repository = new IHarnessPluginRepository() {
                @Override public void save(HarnessPluginEntity plugin) {
                    pluginMap.put(plugin.getPluginId(), plugin);
                }
                @Override public List<HarnessPluginEntity> listAll() {
                    return plugins;
                }
                @Override public Optional<HarnessPluginEntity> findByPluginId(String pluginId) {
                    return Optional.ofNullable(pluginMap.get(pluginId));
                }
                @Override public void deleteByPluginId(String pluginId) {
                    pluginMap.remove(pluginId);
                }
            };
            this.runtimeService = new IPluginRuntimeService() {
                @Override public net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginRunResultVO start(
                        net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity plugin,
                        net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO plan) {
                    throw new AssertionError("runtime should not start plugins in this fixture");
                }

                @Override public net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginProcessStatusVO status(String pluginId) {
                    boolean running = runtimeStatus.getOrDefault(pluginId, false);
                    return new net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginProcessStatusVO(
                            pluginId, 0L, running, "java", null, Instant.now(), running ? "running" : "not running");
                }

                @Override public net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginProcessStatusVO stop(String pluginId) {
                    throw new AssertionError("runtime should not stop plugins in this fixture");
                }
            };
        }
    }
}
