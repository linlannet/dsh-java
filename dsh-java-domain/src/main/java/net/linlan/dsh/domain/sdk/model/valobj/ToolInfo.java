package net.linlan.dsh.domain.sdk.model.valobj;

import java.util.Map;

/**
 * ToolInfo 是工具列表项的信息。
 */
public record ToolInfo(
        String name,
        String description,
        Map<String, Object> inputSchema
) {}
