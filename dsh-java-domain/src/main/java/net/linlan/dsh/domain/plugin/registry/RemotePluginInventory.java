package net.linlan.dsh.domain.plugin.registry;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IRemoteInventoryPort;
import net.linlan.dsh.domain.plugin.registry.adapter.port.IRemoteInventoryPort.PluginInfo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RemotePluginInventory {

    private final IRemoteInventoryPort port;

    public RemotePluginInventory(IRemoteInventoryPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    public List<PluginInfo> listPlugins() {
        return port.listPlugins();
    }

    public Optional<PluginInfo> getPlugin(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            return Optional.empty();
        }
        return port.getPlugin(id);
    }
}
