package net.linlan.dsh.api.dto;

public record PluginInventoryItemDTO(
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
}
