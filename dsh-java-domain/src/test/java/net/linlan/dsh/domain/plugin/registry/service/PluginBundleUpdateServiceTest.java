package net.linlan.dsh.domain.plugin.registry.service;

import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginBundleInfo;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginBundleUpdateKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Plugin Bundle 更新")
class PluginBundleUpdateServiceTest {

    private final PluginBundleUpdateService service = new PluginBundleUpdateService();

    @Test
    @DisplayName("registry 版本不同时返回 REGISTRY")
    void registryUpdate() {
        assertEquals(PluginBundleUpdateKind.REGISTRY,
                service.registryUpdate("1.0.0", "1.1.0").orElseThrow());
        assertTrue(service.registryUpdate("1.0.0", "1.0.0").isEmpty());
        assertTrue(service.registryUpdate(null, "1.1.0").isEmpty());
    }

    @Test
    @DisplayName("Git 固定提交不同时返回 GIT")
    void gitUpdateWithPinnedCommit() {
        String current = "a".repeat(40);
        String latest = "b".repeat(40);

        assertEquals(PluginBundleUpdateKind.GIT,
                service.gitUpdate("https://example.com/repo.git#" + current, latest).orElseThrow());
        assertTrue(service.gitUpdate("https://example.com/repo.git#" + current, current).isEmpty());
    }

    @Test
    @DisplayName("分支/标签等非固定 spec 不提供 Git 更新")
    void gitUpdateRequiresPinnedCommit() {
        String latest = "b".repeat(40);
        assertTrue(service.gitUpdate("https://example.com/repo.git#main", latest).isEmpty());
        assertTrue(service.gitUpdate("https://example.com/repo.git#v1.0.0", latest).isEmpty());
        assertTrue(service.gitUpdate("https://example.com/repo.git", latest).isEmpty());
        assertTrue(service.gitUpdate("https://example.com/repo.git#abc123", latest).isEmpty());
    }

    @Test
    @DisplayName("无更新时 update 为空，listBundles 不输出该字段")
    void bundleWithoutUpdate() {
        PluginBundleInfo info = service.describe("bundle-a", "1.0.0", "abc", false, false);

        assertTrue(info.update().isEmpty());
        PluginBundleInfo registry = service.describe("bundle-a", "1.0.0", "abc", true, true);
        assertEquals(PluginBundleUpdateKind.REGISTRY, registry.update().orElseThrow());
    }
}
