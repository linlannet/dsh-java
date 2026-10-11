package net.linlan.dsh.api.dto;

public record InstallPluginRequestDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String sourcePath,
        String entrypoint
) {
}
