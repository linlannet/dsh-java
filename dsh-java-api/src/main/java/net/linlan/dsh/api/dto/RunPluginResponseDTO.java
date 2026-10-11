package net.linlan.dsh.api.dto;

import java.time.Instant;

public record RunPluginResponseDTO(
        String pluginId,
        long processId,
        String runtimeCommand,
        String workingDirectory,
        boolean running,
        Instant startedAt,
        String message
) {
}
