package net.linlan.dsh.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ConversationMessageResponseDTO(
        String role,
        String content,
        List<Map<String, Object>> blocks,
        Instant occurredAt
) {
}
