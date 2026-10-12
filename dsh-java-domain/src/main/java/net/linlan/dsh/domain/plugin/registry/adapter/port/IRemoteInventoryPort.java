package net.linlan.dsh.domain.plugin.registry.adapter.port;

import java.util.List;
import java.util.Optional;

public interface IRemoteInventoryPort {

    List<PluginInfo> listPlugins();

    Optional<PluginInfo> getPlugin(String id);

    record PluginInfo(
            String id,
            String name,
            String version,
            String description,
            String sourceUrl
    ) {}
}
