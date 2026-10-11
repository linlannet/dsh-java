package net.linlan.dsh.api.dto;

import java.util.Map;

public record RuntimeApprovalResponseDTO(
        String approvalId,
        String sessionId,
        String toolName,
        String displayCommand,
        Map<String, Object> arguments,
        String createdAt
) {}
