package net.linlan.dsh.api.gateway.dto;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.StartWorkflowRequestDTO;

public record GatewayStreamRequestDTO(
        String source,
        AgentMessageRequestDTO agent,
        StartWorkflowRequestDTO workflow
) {
    public GatewayStreamRequestDTO {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must be non-blank");
        }
    }
}
