package net.linlan.dsh.domain.tool.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * IMcpClient 表示IMcpClient。
 */
public interface IMcpClient {

    /**
     * 处理connect。
     * @return 领域处理结果。
     */
    CompletableFuture<Void> connect();

    /**
     * 处理disconnect。
     * @return 领域处理结果。
     */
    CompletableFuture<Void> disconnect();

    /**
     * 判断Connected是否成立。
     * @return 领域处理结果。
     */
    boolean isConnected();

    /**
     * 处理listToolsTool列表。
     * @return 领域处理结果。
     */
    CompletableFuture<List<McpToolDescriptor>> listTools();

    /**
     * McpToolDescriptor 表示McpToolDescriptor。
     */
    CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments);

    /**
     * McpToolDescriptor 表示McpToolDescriptor。
     */
    record McpToolDescriptor(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {}

    /**
     * McpToolResult 表示McpToolResult结果。
     */
    record McpToolResult(
            List<Map<String, Object>> content,
            boolean isError
    ) {}
}
