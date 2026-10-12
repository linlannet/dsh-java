package net.linlan.dsh.domain.plugin.registry;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IRemoteInventoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemotePluginInventory 远程插件清单")
class RemotePluginInventoryTest {

    @Test
    @DisplayName("listPlugins 委派 port 返回列表")
    void listPluginsDelegates() {
        var port = new StubPort(List.of(
                new IRemoteInventoryPort.PluginInfo("a", "A", "1.0", "desc a", "url-a"),
                new IRemoteInventoryPort.PluginInfo("b", "B", "2.0", "desc b", "url-b")
        ), Optional.empty());
        var inventory = new RemotePluginInventory(port);
        var plugins = inventory.listPlugins();
        assertEquals(2, plugins.size());
        assertEquals("a", plugins.get(0).id());
    }

    @Test
    @DisplayName("getPlugin 委派 port 返回插件")
    void getPluginDelegates() {
        var info = new IRemoteInventoryPort.PluginInfo("x", "X", "1.0", "d", "u");
        var port = new StubPort(List.of(), Optional.of(info));
        var inventory = new RemotePluginInventory(port);
        var result = inventory.getPlugin("x");
        assertTrue(result.isPresent());
        assertEquals("x", result.get().id());
    }

    @Test
    @DisplayName("getPlugin 空白 id 返回 empty")
    void getPluginBlankIdReturnsEmpty() {
        var port = new StubPort(List.of(), Optional.empty());
        var inventory = new RemotePluginInventory(port);
        assertTrue(inventory.getPlugin("").isEmpty());
        assertTrue(inventory.getPlugin("  ").isEmpty());
    }

    @Test
    @DisplayName("getPlugin null 抛出 NPE")
    void getPluginNullThrows() {
        var port = new StubPort(List.of(), Optional.empty());
        var inventory = new RemotePluginInventory(port);
        assertThrows(NullPointerException.class, () -> inventory.getPlugin(null));
    }

    @Test
    @DisplayName("构造 null port 抛出 NPE")
    void nullPortThrows() {
        assertThrows(NullPointerException.class, () -> new RemotePluginInventory(null));
    }

    @Test
    @DisplayName("listPlugins 空清单返回空列表")
    void listPluginsEmpty() {
        var port = new StubPort(List.of(), Optional.empty());
        var inventory = new RemotePluginInventory(port);
        assertTrue(inventory.listPlugins().isEmpty());
    }

    private static final class StubPort implements IRemoteInventoryPort {
        private final List<PluginInfo> list;
        private final Optional<PluginInfo> single;

        StubPort(List<PluginInfo> list, Optional<PluginInfo> single) {
            this.list = list;
            this.single = single;
        }

        @Override
        public List<PluginInfo> listPlugins() {
            return list;
        }

        @Override
        public Optional<PluginInfo> getPlugin(String id) {
            return single;
        }
    }
}
