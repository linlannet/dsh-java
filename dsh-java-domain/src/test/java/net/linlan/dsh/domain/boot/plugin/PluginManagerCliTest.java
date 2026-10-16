package net.linlan.dsh.domain.boot.plugin;

import net.linlan.dsh.domain.plugin.IPluginBridgeService;
import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallationResultVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginManagerCli 插件管理")
class PluginManagerCliTest {

    private static class StubRegistry implements IPluginRegistryService {
        final Map<String, HarnessPluginEntity> store = new HashMap<>();

        @Override
        public PluginInstallationResultVO installPlugin(String pluginId, String displayName, String pluginVersion,
                                                         String runtimeType, String sourcePath, String entrypoint) {
            var entity = new HarnessPluginEntity(pluginId, displayName, pluginVersion,
                    PluginRuntimeTypeEnumVO.JAVA_NATIVE, PluginInstallModeEnumVO.NATIVE,
                    sourcePath, entrypoint, PluginInstallStatusEnumVO.REGISTERED, Instant.now());
            store.put(pluginId, entity);
            return new PluginInstallationResultVO(pluginId, PluginRuntimeTypeEnumVO.JAVA_NATIVE,
                    PluginInstallModeEnumVO.NATIVE, PluginInstallStatusEnumVO.REGISTERED, true, Instant.now());
        }

        @Override
        public HarnessPluginEntity loadPlugin(String pluginId) {
            return store.get(pluginId);
        }

        @Override
        public PluginInstallStatusEnumVO refreshStatus(HarnessPluginEntity plugin, boolean bridgeReady) {
            return PluginInstallStatusEnumVO.ACTIVE;
        }

        @Override
        public PluginInstallStatusEnumVO disablePlugin(String pluginId) {
            return PluginInstallStatusEnumVO.DISABLED;
        }

        @Override
        public PluginInstallStatusEnumVO enablePlugin(String pluginId) {
            return PluginInstallStatusEnumVO.ACTIVE;
        }

        @Override
        public PluginInstallStatusEnumVO uninstallPlugin(String pluginId) {
            store.remove(pluginId);
            return PluginInstallStatusEnumVO.UNINSTALLED;
        }

        @Override
        public Optional<HarnessPluginEntity> loadPluginOptional(String pluginId) {
            return Optional.ofNullable(store.get(pluginId));
        }
    }

    private static class StubBridge implements IPluginBridgeService {
        boolean inspected = false;

        @Override
        public PluginBridgePlanVO inspect(HarnessPluginEntity plugin) {
            inspected = true;
            return null;
        }

        @Override
        public void recordBinding(String pluginId, PluginBridgePlanVO plan, Instant checkedAt) {
        }
    }

    @Test
    @DisplayName("install 调用 registryService 安装插件")
    void install() {
        var registry = new StubRegistry();
        var bridge = new StubBridge();
        var cli = new PluginManagerCli(registry, bridge);

        var result = cli.install("p1", "Plugin 1", "1.0.0", "java", "/path", "Main");
        assertEquals("p1", result.pluginId());
    }

    @Test
    @DisplayName("uninstall 已存在插件返回 true")
    void uninstallExists() {
        var registry = new StubRegistry();
        var bridge = new StubBridge();
        var cli = new PluginManagerCli(registry, bridge);

        cli.install("p1", "Plugin 1", "1.0.0", "java", "/path", "Main");
        assertTrue(cli.uninstall("p1"));
    }

    @Test
    @DisplayName("uninstall 不存在插件返回 false")
    void uninstallNotExists() {
        var registry = new StubRegistry();
        var bridge = new StubBridge();
        var cli = new PluginManagerCli(registry, bridge);

        assertFalse(cli.uninstall("nonexistent"));
    }

    @Test
    @DisplayName("info 返回插件信息")
    void info() {
        var registry = new StubRegistry();
        var bridge = new StubBridge();
        var cli = new PluginManagerCli(registry, bridge);

        cli.install("p1", "Plugin 1", "1.0.0", "java", "/path", "Main");
        Optional<HarnessPluginEntity> info = cli.info("p1");
        assertTrue(info.isPresent());
    }

    @Test
    @DisplayName("inspect 调用 bridgeService")
    void inspect() {
        var registry = new StubRegistry();
        var bridge = new StubBridge();
        var cli = new PluginManagerCli(registry, bridge);

        cli.install("p1", "Plugin 1", "1.0.0", "java", "/path", "Main");
        cli.inspect("p1");
        assertTrue(bridge.inspected);
    }
}
