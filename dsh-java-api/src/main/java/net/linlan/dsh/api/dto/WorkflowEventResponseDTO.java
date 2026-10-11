package net.linlan.dsh.api.dto;

public record WorkflowEventResponseDTO(
        String runId,
        String event,
        String status,
        String output,
        String error
) {
}
