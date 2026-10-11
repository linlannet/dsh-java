package net.linlan.dsh.domain.tool.mcp;

import net.linlan.dsh.domain.tool.mcp.model.valobj.ReconnectPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ReconnectingMcpClient 装饰器包裹底层 {@link IMcpClient}，断连后按 {@link ReconnectPolicy} 自动重连。
 */
public class ReconnectingMcpClient implements IMcpClient {

    private static final Logger log = LoggerFactory.getLogger(ReconnectingMcpClient.class);

    private final IMcpClient delegate;
    private final ReconnectPolicy policy;
    private final String serverName;

    public ReconnectingMcpClient(String serverName, IMcpClient delegate, ReconnectPolicy policy) {
        this.serverName = serverName;
        this.delegate = delegate;
        this.policy = policy;
    }

    public ReconnectingMcpClient(String serverName, IMcpClient delegate) {
        this(serverName, delegate, ReconnectPolicy.default_());
    }

    @Override
    public CompletableFuture<Void> connect() {
        return delegate.connect();
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return delegate.disconnect();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public CompletableFuture<List<McpToolDescriptor>> listTools() {
        if (!delegate.isConnected()) {
            reconnect();
        }
        return delegate.listTools().exceptionallyCompose(e -> {
            log.warn("[MCP] listTools failed for '{}', attempting reconnect: {}", serverName, e.getMessage());
            reconnect();
            return delegate.listTools();
        });
    }

    @Override
    public CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments) {
        if (!delegate.isConnected()) {
            reconnect();
        }
        return delegate.callTool(toolName, arguments).exceptionallyCompose(e -> {
            log.warn("[MCP] callTool '{}' failed for '{}', attempting reconnect: {}", toolName, serverName, e.getMessage());
            reconnect();
            return delegate.callTool(toolName, arguments);
        });
    }

    private void reconnect() {
        Throwable lastError = null;
        for (int attempt = 0; attempt < policy.maxAttempts(); attempt++) {
            long backoff = policy.nextBackoff(attempt);
            log.info("[MCP] Reconnecting '{}' attempt {}/{}, backoff {}ms", serverName, attempt + 1, policy.maxAttempts(), backoff);
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpReconnectExhaustedException(serverName, attempt, e);
            }
            try {
                delegate.disconnect();
            } catch (Exception ignored) {
            }
            try {
                delegate.connect().join();
                if (delegate.isConnected()) {
                    log.info("[MCP] Reconnected '{}' successfully on attempt {}", serverName, attempt + 1);
                    return;
                }
            } catch (Exception e) {
                lastError = e;
                log.warn("[MCP] Reconnect attempt {} failed for '{}': {}", attempt + 1, serverName, e.getMessage());
            }
        }
        throw new McpReconnectExhaustedException(serverName, policy.maxAttempts(), lastError);
    }
}
