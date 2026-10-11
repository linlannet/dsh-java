package net.linlan.dsh.api.dto;

import java.time.Instant;

public record InstallPluginResponseDTO(
        String pluginId,
        String runtimeType,
        String installMode,
        String status,
        boolean directlyExecutable,
        Instant installedAt
) {
}
