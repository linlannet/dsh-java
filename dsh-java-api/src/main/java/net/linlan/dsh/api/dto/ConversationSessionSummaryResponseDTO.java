package net.linlan.dsh.api.dto;

import java.time.Instant;

public record ConversationSessionSummaryResponseDTO(
        String sessionId,
        String title,
        String preview,
        String status,
        Instant createdAt,
        String agentId,
        String workspaceId
) {
}
