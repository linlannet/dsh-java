package net.linlan.dsh.api.dto;

import java.time.Instant;

public record ActivatePluginResponseDTO(
        String pluginId,
        String status,
        String packageType,
        String runtimeCommand,
        boolean bridgeReady,
        String message,
        Instant checkedAt
) {
}
