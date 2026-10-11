package net.linlan.dsh.domain.plugin.registry.model.valobj;

import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;

public record PluginInventoryItemVO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String installMode,
        String status,
        String sourcePath,
        String entrypoint,
        String manifestPath,
        String author,
        String description,
        String scope
) {
    public static PluginInventoryItemVO from(
            HarnessPluginEntity plugin,
            PluginInventoryMetadataVO metadata
    ) {
        return new PluginInventoryItemVO(
                plugin.getPluginId(),
                plugin.getDisplayName(),
                plugin.getPluginVersion(),
                plugin.getRuntimeType().name(),
                plugin.getInstallMode().name(),
                plugin.getStatus().name(),
                plugin.getSourcePath(),
                plugin.getEntrypoint(),
                metadata == null ? null : metadata.manifestPath(),
                metadata == null ? null : metadata.author(),
                metadata == null ? null : metadata.description(),
                metadata == null ? "unknown" : metadata.scope()
        );
    }
}
