package net.linlan.dsh.api.dto;

import java.time.Instant;

public record InstalledPluginResponseDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String installMode,
        String status,
        String sourcePath,
        String entrypoint,
        Instant installedAt
) {
}
