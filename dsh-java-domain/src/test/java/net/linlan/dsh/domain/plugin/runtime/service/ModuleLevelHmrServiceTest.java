package net.linlan.dsh.domain.plugin.runtime.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T1.033 ModuleLevelHmrService 模块级 HMR")
class ModuleLevelHmrServiceTest {

    private ModuleLevelHmrService hmr;

    @AfterEach
    void cleanup() {
        if (hmr != null) {
            hmr.stop();
        }
    }

    @Test
    @DisplayName("watchModule 注册模块监听")
    void watchModuleRegisters() throws Exception {
        hmr = new ModuleLevelHmrService(100L);
        Path tempDir = Files.createTempDirectory("hmr-test");

        hmr.watchModule("mod-1", tempDir);

        assertTrue(hmr.isWatching("mod-1"));
        assertEquals(1, hmr.watchedModuleCount());
    }

    @Test
    @DisplayName("unwatchModule 取消模块监听")
    void unwatchModuleRemoves() throws Exception {
        hmr = new ModuleLevelHmrService(100L);
        Path tempDir = Files.createTempDirectory("hmr-test");

        hmr.watchModule("mod-1", tempDir);
        hmr.unwatchModule("mod-1");

        assertFalse(hmr.isWatching("mod-1"));
        assertEquals(0, hmr.watchedModuleCount());
    }

    @Test
    @DisplayName("createClassLoader 为模块创建独立 URLClassLoader")
    void createClassLoaderPerModule() throws Exception {
        hmr = new ModuleLevelHmrService();
        URL[] urls = new URL[0];

        URLClassLoader cl1 = hmr.createClassLoader("mod-1", urls);
        URLClassLoader cl2 = hmr.createClassLoader("mod-2", urls);

        assertNotSame(cl1, cl2);
        assertSame(cl1, hmr.getClassLoader("mod-1"));
        assertSame(cl2, hmr.getClassLoader("mod-2"));
    }

    @Test
    @DisplayName("createClassLoader 替换旧 ClassLoader 并关闭")
    void replaceClassLoaderClosesOld() throws Exception {
        hmr = new ModuleLevelHmrService();
        URL[] urls = new URL[0];

        URLClassLoader old = hmr.createClassLoader("mod-1", urls);
        URLClassLoader newCl = hmr.createClassLoader("mod-1", urls);

        assertSame(newCl, hmr.getClassLoader("mod-1"));
        assertNotSame(old, newCl);
    }

    @Test
    @DisplayName("unwatchModule 关闭对应 ClassLoader")
    void unwatchClosesClassLoader() throws Exception {
        hmr = new ModuleLevelHmrService();
        URL[] urls = new URL[0];

        hmr.createClassLoader("mod-1", urls);
        hmr.unwatchModule("mod-1");

        assertNull(hmr.getClassLoader("mod-1"));
    }

    @Test
    @DisplayName("refreshManifest 调用 ManifestRefresher")
    void refreshManifestCallsRefresher() {
        hmr = new ModuleLevelHmrService();
        var called = new java.util.concurrent.atomic.AtomicBoolean(false);
        hmr.setManifestRefresher(moduleId -> called.set(true));

        hmr.refreshManifest("mod-1");

        assertTrue(called.get());
    }

    @Test
    @DisplayName("refreshResolution 调用 ResolutionRefresher")
    void refreshResolutionCallsRefresher() {
        hmr = new ModuleLevelHmrService();
        var called = new java.util.concurrent.atomic.AtomicBoolean(false);
        hmr.setResolutionRefresher(moduleId -> called.set(true));

        hmr.refreshResolution("mod-1");

        assertTrue(called.get());
    }

    @Test
    @DisplayName("refreshManifest 无 refresher 时不抛异常")
    void refreshManifestNoRefresherNoException() {
        hmr = new ModuleLevelHmrService();
        assertDoesNotThrow(() -> hmr.refreshManifest("mod-1"));
    }

    @Test
    @DisplayName("stop 清理所有监听")
    void stopClearsAllWatches() throws Exception {
        hmr = new ModuleLevelHmrService(100L);
        Path tempDir1 = Files.createTempDirectory("hmr-test-1");
        Path tempDir2 = Files.createTempDirectory("hmr-test-2");

        hmr.watchModule("mod-1", tempDir1);
        hmr.watchModule("mod-2", tempDir2);
        hmr.stop();

        assertEquals(0, hmr.watchedModuleCount());
    }
}
