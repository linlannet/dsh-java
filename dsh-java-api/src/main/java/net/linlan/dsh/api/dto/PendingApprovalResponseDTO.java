package net.linlan.dsh.api.dto;

import java.time.Instant;

public record PendingApprovalResponseDTO(
        String sessionId,
        String title,
        String preview,
        String status,
        Instant createdAt
) {
}
