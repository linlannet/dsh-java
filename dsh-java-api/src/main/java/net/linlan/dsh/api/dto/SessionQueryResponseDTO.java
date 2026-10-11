package net.linlan.dsh.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionQueryResponseDTO(
        String sessionId,
        String workspaceId,
        String status,
        String modelCode,
        String profileCode,
        Instant createdAt,
        Instant lastActivityAt,
        List<Map<String, Object>> messages
) {
}
