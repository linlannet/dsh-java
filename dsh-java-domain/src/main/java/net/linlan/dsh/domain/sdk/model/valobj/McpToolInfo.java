package net.linlan.dsh.domain.sdk.model.valobj;

/**
 * McpToolInfo 是 MCP 工具列表项的信息。
 */
public record McpToolInfo(
        String serverName,
        String toolName,
        String description
) {}
