package net.linlan.dsh.api.dto;

import java.time.Instant;
import java.util.List;

public record PluginInventoryResponseDTO(
        Instant generatedAt,
        long count,
        List<PluginInventoryItemDTO> plugins
) {
}
