package net.linlan.dsh.api.dto;

public record SessionListRequestDTO(
        String workspaceId,
        int offset,
        int limit
) {
}
