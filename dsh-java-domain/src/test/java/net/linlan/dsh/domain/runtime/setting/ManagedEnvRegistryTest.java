package net.linlan.dsh.domain.runtime.setting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManagedEnvRegistry DSH_ 受管环境变量注册表")
class ManagedEnvRegistryTest {

    @Test
    @DisplayName("register DSH_ 前缀键成功")
    void registerDshPrefixKeySucceeds() {
        var registry = new ManagedEnvRegistry();
        registry.register("DSH_API_KEY", "secret");
        assertTrue(registry.contains("DSH_API_KEY"));
        assertEquals("secret", registry.get("DSH_API_KEY"));
    }

    @Test
    @DisplayName("register 非 DSH_ 前缀键抛出异常")
    void registerNonDshPrefixThrows() {
        var registry = new ManagedEnvRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register("API_KEY", "secret"));
    }

    @Test
    @DisplayName("register null 键抛出异常")
    void registerNullKeyThrows() {
        var registry = new ManagedEnvRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(null, "v"));
    }

    @Test
    @DisplayName("validate 合法键返回 true")
    void validateValidKeyReturnsTrue() {
        var registry = new ManagedEnvRegistry();
        assertTrue(registry.validate("DSH_FOO"));
    }

    @Test
    @DisplayName("validate 非法键抛出异常")
    void validateInvalidKeyThrows() {
        var registry = new ManagedEnvRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.validate("FOO"));
    }

    @Test
    @DisplayName("unregister 移除键")
    void unregisterRemovesKey() {
        var registry = new ManagedEnvRegistry();
        registry.register("DSH_A", "1");
        registry.unregister("DSH_A");
        assertFalse(registry.contains("DSH_A"));
    }

    @Test
    @DisplayName("list 返回所有注册项")
    void listReturnsAllEntries() {
        var registry = new ManagedEnvRegistry();
        registry.register("DSH_A", "1");
        registry.register("DSH_B", "2");
        Map<String, String> list = registry.list();
        assertEquals(2, list.size());
        assertEquals("1", list.get("DSH_A"));
        assertEquals("2", list.get("DSH_B"));
    }

    @Test
    @DisplayName("list 返回不可变 Map")
    void listReturnsUnmodifiableMap() {
        var registry = new ManagedEnvRegistry();
        registry.register("DSH_A", "1");
        Map<String, String> list = registry.list();
        assertThrows(UnsupportedOperationException.class, () -> list.put("DSH_C", "3"));
    }

    @Test
    @DisplayName("inject 将变量注入 ProcessBuilder")
    void injectPopulatesProcessBuilder() {
        var registry = new ManagedEnvRegistry();
        registry.register("DSH_INJECTED", "yes");
        ProcessBuilder pb = new ProcessBuilder("echo", "test");
        registry.inject(pb);
        assertEquals("yes", pb.environment().get("DSH_INJECTED"));
    }

    @Test
    @DisplayName("inject null builder 抛出异常")
    void injectNullBuilderThrows() {
        var registry = new ManagedEnvRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.inject(null));
    }

    @Test
    @DisplayName("clear 清空所有注册")
    void clearRemovesAll() {
        var registry = new ManagedEnvRegistry();
        registry.register("DSH_A", "1");
        registry.register("DSH_B", "2");
        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("size 返回注册数量")
    void sizeReturnsCount() {
        var registry = new ManagedEnvRegistry();
        assertEquals(0, registry.size());
        registry.register("DSH_A", "1");
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("get 不存在键返回 null")
    void getMissingKeyReturnsNull() {
        var registry = new ManagedEnvRegistry();
        assertNull(registry.get("DSH_MISSING"));
    }
}
