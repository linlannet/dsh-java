package net.linlan.dsh.domain.plugin.registry.service;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IPluginArtifactInstallerPort;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginArtifactInstallPlanVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallationResultVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.domain.plugin.runtime.adapter.port.IRuntimeResolutionPort;
import net.linlan.dsh.domain.plugin.runtime.model.valobj.ResolvedPackage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryServiceTest {

    @Test
    void installPluginPersistsJavaNativeAsNativeMode() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        RecordingInstallerPort installer = new RecordingInstallerPort();
        PluginRegistryService service = new PluginRegistryService(repo, installer);

        PluginInstallationResultVO result = service.installPlugin(
                "time-tools", "Time Tools", "1.0.0",
                "JAVA_NATIVE", "/tmp/time.jar", "net.example.TimeTools"
        );

        assertEquals("time-tools", result.pluginId());
        assertEquals(PluginRuntimeTypeEnumVO.JAVA_NATIVE, result.runtimeType());
        assertEquals(PluginInstallModeEnumVO.NATIVE, result.installMode());
        assertEquals(PluginInstallStatusEnumVO.REGISTERED, result.status());
        assertTrue(result.directlyExecutable());
        assertNotNull(result.installedAt());

        HarnessPluginEntity stored = repo.find("time-tools");
        assertEquals(PluginInstallStatusEnumVO.REGISTERED, stored.getStatus());
        assertEquals("/tmp/time.jar", installer.lastInstalledSourcePath);
    }

    @Test
    void installPluginPersistsNodeBridgeAsNodeBridgeMode() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());

        PluginInstallationResultVO result = service.installPlugin(
                "node-tools", "Node Tools", "2.0.0",
                "DSH_NODE_BRIDGE", "/tmp/node.js", "./index.js"
        );

        assertEquals(PluginRuntimeTypeEnumVO.DSH_NODE_BRIDGE, result.runtimeType());
        assertEquals(PluginInstallModeEnumVO.NODE_BRIDGE, result.installMode());
        assertEquals(PluginInstallStatusEnumVO.REGISTERED, result.status());
        assertTrue(!result.directlyExecutable());
    }

    @Test
    void installPluginRejectsBlankPluginId() {
        PluginRegistryService service = new PluginRegistryService(
                new InMemoryPluginRepository(), new RecordingInstallerPort());

        assertThrows(IllegalArgumentException.class, () -> service.installPlugin(
                "  ", "name", "1.0.0", "JAVA_NATIVE", "/p", "e"));
    }

    @Test
    void installPluginRejectsInvalidPluginIdCharacters() {
        PluginRegistryService service = new PluginRegistryService(
                new InMemoryPluginRepository(), new RecordingInstallerPort());

        assertThrows(IllegalArgumentException.class, () -> service.installPlugin(
                "@bad", "name", "1.0.0", "JAVA_NATIVE", "/p", "e"));
    }

    @Test
    void loadPluginReturnsEntityWhenPresent() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");

        HarnessPluginEntity loaded = service.loadPlugin("p1");

        assertEquals("p1", loaded.getPluginId());
    }

    @Test
    void loadPluginThrowsWhenAbsent() {
        PluginRegistryService service = new PluginRegistryService(
                new InMemoryPluginRepository(), new RecordingInstallerPort());

        assertThrows(IllegalArgumentException.class, () -> service.loadPlugin("nope"));
    }

    @Test
    void loadPluginOptionalReturnsEmptyWhenAbsent() {
        PluginRegistryService service = new PluginRegistryService(
                new InMemoryPluginRepository(), new RecordingInstallerPort());

        assertTrue(service.loadPluginOptional("nope").isEmpty());
    }

    @Test
    void refreshStatusActivatesWhenBridgeReady() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");
        HarnessPluginEntity plugin = service.loadPlugin("p1");

        PluginInstallStatusEnumVO status = service.refreshStatus(plugin, true);

        assertEquals(PluginInstallStatusEnumVO.ACTIVE, status);
        assertEquals(PluginInstallStatusEnumVO.ACTIVE, service.loadPlugin("p1").getStatus());
    }

    @Test
    void refreshStatusFailsWhenBridgeNotReady() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");
        HarnessPluginEntity plugin = service.loadPlugin("p1");

        PluginInstallStatusEnumVO status = service.refreshStatus(plugin, false);

        assertEquals(PluginInstallStatusEnumVO.FAILED, status);
    }

    @Test
    void disablePluginTransitionsToDisabled() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");

        assertEquals(PluginInstallStatusEnumVO.DISABLED, service.disablePlugin("p1"));
    }

    @Test
    void enablePluginTransitionsBackToRegistered() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");
        service.disablePlugin("p1");

        assertEquals(PluginInstallStatusEnumVO.REGISTERED, service.enablePlugin("p1"));
    }

    @Test
    void uninstallPluginMarksUninstalledAndInvokesArtifactPort() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        RecordingInstallerPort installer = new RecordingInstallerPort();
        PluginRegistryService service = new PluginRegistryService(repo, installer);
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");

        PluginInstallStatusEnumVO status = service.uninstallPlugin("p1");

        assertEquals(PluginInstallStatusEnumVO.UNINSTALLED, status);
        assertTrue(installer.uninstalled.contains("p1"));
    }

    @Test
    void refreshResolutionIsNoopWhenResolutionPortAbsent() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        PluginRegistryService service = new PluginRegistryService(repo, new RecordingInstallerPort());
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");

        service.refreshResolution("p1");
    }

    @Test
    void refreshResolutionRegistersPackageWhenResolveReturnsEmpty() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        RecordingInstallerPort installer = new RecordingInstallerPort();
        PluginRegistryService service = new PluginRegistryService(repo, installer);
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");
        RecordingResolutionPort resolutionPort = new RecordingResolutionPort();
        PluginRegistryService withResolution = new PluginRegistryService(repo, installer, resolutionPort);

        withResolution.refreshResolution("p1");

        assertTrue(resolutionPort.refreshed.contains("p1"));
        assertEquals(1, resolutionPort.registered.size());
        ResolvedPackage pkg = resolutionPort.registered.get(0);
        assertEquals("p1", pkg.moduleId());
        assertEquals("JAVA_NATIVE", pkg.packageType());
    }

    @Test
    void refreshResolutionDoesNotRegisterWhenAlreadyResolved() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        RecordingInstallerPort installer = new RecordingInstallerPort();
        PluginRegistryService service = new PluginRegistryService(repo, installer);
        service.installPlugin("p1", "P1", "1.0.0", "JAVA_NATIVE", "/p", "e");
        RecordingResolutionPort resolutionPort = new RecordingResolutionPort();
        resolutionPort.existing = new ResolvedPackage("p1", "e", "/p", "JAVA_NATIVE", Instant.now());
        PluginRegistryService withResolution = new PluginRegistryService(repo, installer, resolutionPort);

        withResolution.refreshResolution("p1");

        assertTrue(resolutionPort.refreshed.contains("p1"));
        assertTrue(resolutionPort.registered.isEmpty());
    }

    @Test
    void refreshResolutionIsNoopWhenPluginMissing() {
        InMemoryPluginRepository repo = new InMemoryPluginRepository();
        RecordingInstallerPort installer = new RecordingInstallerPort();
        RecordingResolutionPort resolutionPort = new RecordingResolutionPort();
        PluginRegistryService service = new PluginRegistryService(repo, installer, resolutionPort);

        service.refreshResolution("absent");

        assertTrue(resolutionPort.refreshed.isEmpty());
        assertTrue(resolutionPort.registered.isEmpty());
    }

    private static final class InMemoryPluginRepository implements IHarnessPluginRepository {
        private final Map<String, HarnessPluginEntity> store = new HashMap<>();

        @Override
        public void save(HarnessPluginEntity plugin) {
            store.put(plugin.getPluginId(), plugin);
        }

        @Override
        public Optional<HarnessPluginEntity> findByPluginId(String pluginId) {
            return Optional.ofNullable(store.get(pluginId));
        }

        @Override
        public List<HarnessPluginEntity> listAll() {
            return List.copyOf(store.values());
        }

        @Override
        public void deleteByPluginId(String pluginId) {
            store.remove(pluginId);
        }

        HarnessPluginEntity find(String pluginId) {
            return store.get(pluginId);
        }
    }

    private static final class RecordingInstallerPort implements IPluginArtifactInstallerPort {
        String lastInstalledSourcePath;
        final List<String> uninstalled = new ArrayList<>();

        @Override
        public PluginArtifactInstallPlanVO install(String pluginId, String sourcePath, String entrypoint) {
            lastInstalledSourcePath = sourcePath;
            return new PluginArtifactInstallPlanVO(sourcePath, entrypoint);
        }

        @Override
        public void uninstall(String pluginId) {
            uninstalled.add(pluginId);
        }
    }

    private static final class RecordingResolutionPort implements IRuntimeResolutionPort {
        final List<String> refreshed = new ArrayList<>();
        final List<ResolvedPackage> registered = new ArrayList<>();
        ResolvedPackage existing;

        @Override
        public Map<String, ResolvedPackage> resolutionMap() {
            return existing == null ? Map.of() : Map.of(existing.moduleId(), existing);
        }

        @Override
        public void refresh(String moduleId) {
            refreshed.add(moduleId);
        }

        @Override
        public Optional<ResolvedPackage> resolve(String moduleId) {
            return Optional.ofNullable(existing);
        }

        @Override
        public void register(ResolvedPackage pkg) {
            registered.add(pkg);
        }
    }
}
