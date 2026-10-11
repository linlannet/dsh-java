package net.linlan.dsh.domain.tool.mcp;

/**
 * McpReconnectExhaustedException 在 MCP 客户端重连次数耗尽时抛出。
 */
public class McpReconnectExhaustedException extends RuntimeException {

    private final String serverName;
    private final int attempts;

    public McpReconnectExhaustedException(String serverName, int attempts, Throwable cause) {
        super("MCP server '" + serverName + "' reconnect exhausted after " + attempts + " attempts", cause);
        this.serverName = serverName;
        this.attempts = attempts;
    }

    public String serverName() {
        return serverName;
    }

    public int attempts() {
        return attempts;
    }
}
