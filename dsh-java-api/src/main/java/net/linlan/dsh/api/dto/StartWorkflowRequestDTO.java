package net.linlan.dsh.api.dto;

import java.util.Map;

public record StartWorkflowRequestDTO(
        String script,
        String name,
        String parentCwd,
        Map<String, Object> args
) {
}
