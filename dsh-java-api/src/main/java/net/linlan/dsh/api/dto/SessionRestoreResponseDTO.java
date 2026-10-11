package net.linlan.dsh.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionRestoreResponseDTO(
        String sessionId,
        boolean restored,
        String agentPhase,
        int turnCount,
        int stepCount,
        String lastTurnEndReason,
        List<Map<String, Object>> messages,
        Instant restoredAt
) {
}
