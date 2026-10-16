package net.linlan.dsh.domain.host.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginInventoryProjection 插件清单")
class PluginInventoryProjectionTest {

    @Test
    @DisplayName("register 和 list 返回只读快照")
    void registerAndList() {
        var projection = new PluginInventoryProjection();
        projection.register("p1", "Plugin 1", "1.0.0", "ACTIVE");
        projection.register("p2", "Plugin 2", "2.0.0", "DISABLED");

        var list = projection.list();
        assertEquals(2, list.size());
        assertEquals(2, projection.count());
    }

    @Test
    @DisplayName("get 返回指定插件")
    void getPlugin() {
        var projection = new PluginInventoryProjection();
        projection.register("p1", "Plugin 1", "1.0.0", "ACTIVE");

        PluginInventoryProjection.PluginEntry entry = projection.get("p1");
        assertNotNull(entry);
        assertEquals("Plugin 1", entry.displayName());
    }

    @Test
    @DisplayName("list 返回不可变快照")
    void listImmutable() {
        var projection = new PluginInventoryProjection();
        projection.register("p1", "Plugin 1", "1.0.0", "ACTIVE");

        var list = projection.list();
        assertThrows(UnsupportedOperationException.class, () -> list.add(null));
    }

    @Test
    @DisplayName("get 不存在返回 null")
    void getNotExists() {
        var projection = new PluginInventoryProjection();
        assertNull(projection.get("nonexistent"));
    }
}
