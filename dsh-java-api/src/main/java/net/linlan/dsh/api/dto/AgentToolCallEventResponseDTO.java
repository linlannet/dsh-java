package net.linlan.dsh.api.dto;

public record AgentToolCallEventResponseDTO(
        String callId,
        String toolName,
        String arguments,
        String status,
        String phase,
        String result,
        Long durationMs
) {
    public AgentToolCallEventResponseDTO(String callId, String toolName, String arguments, String status) {
        this(callId, toolName, arguments, status, "call", null, null);
    }
}
