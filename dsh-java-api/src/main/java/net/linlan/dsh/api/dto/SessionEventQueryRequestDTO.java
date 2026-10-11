package net.linlan.dsh.api.dto;

import java.util.List;

public record SessionEventQueryRequestDTO(
        String sessionId,
        Long fromSeq,
        Long toSeq,
        List<String> eventTypes
) {
}
