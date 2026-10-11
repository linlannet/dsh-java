package net.linlan.dsh.domain.tool.mcp;

import net.linlan.dsh.domain.tool.mcp.model.valobj.ReconnectPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T1.031 ReconnectPolicy 与 ReconnectingMcpClient")
class ReconnectingMcpClientTest {

    @Test
    @DisplayName("ReconnectPolicy.default_() 创建合理默认值")
    void defaultPolicy() {
        var policy = ReconnectPolicy.default_();
        assertEquals(100L, policy.initialBackoff());
        assertEquals(30_000L, policy.maxBackoff());
        assertEquals(5, policy.maxAttempts());
        assertEquals(2.0, policy.backoffMultiplier());
    }

    @Test
    @DisplayName("ReconnectPolicy.nextBackoff() 指数退避")
    void exponentialBackoff() {
        var policy = new ReconnectPolicy(100L, 10_000L, 5, 2.0);
        assertEquals(100L, policy.nextBackoff(0));
        assertEquals(200L, policy.nextBackoff(1));
        assertEquals(400L, policy.nextBackoff(2));
        assertEquals(800L, policy.nextBackoff(3));
        assertEquals(1600L, policy.nextBackoff(4));
    }

    @Test
    @DisplayName("ReconnectPolicy.nextBackoff() 不超过 maxBackoff")
    void backoffCapped() {
        var policy = new ReconnectPolicy(1000L, 5000L, 10, 2.0);
        assertEquals(1000L, policy.nextBackoff(0));
        assertEquals(2000L, policy.nextBackoff(1));
        assertEquals(4000L, policy.nextBackoff(2));
        assertEquals(5000L, policy.nextBackoff(3));
        assertEquals(5000L, policy.nextBackoff(10));
    }

    @Test
    @DisplayName("ReconnectingMcpClient 透传已连接客户端的调用")
    void passthroughWhenConnected() {
        var stub = new StubMcpClient(true);
        var wrapper = new ReconnectingMcpClient("test-server", stub, new ReconnectPolicy(10L, 100L, 3, 2.0));

        var tools = wrapper.listTools().join();

        assertEquals(1, tools.size());
        assertEquals("tool1", tools.get(0).name());
        assertEquals(0, stub.reconnectCount.get());
    }

    @Test
    @DisplayName("断连后自动重连成功")
    void reconnectOnDisconnect() {
        var stub = new StubMcpClient(false);
        var wrapper = new ReconnectingMcpClient("test-server", stub, new ReconnectPolicy(1L, 10L, 3, 2.0));

        var tools = wrapper.listTools().join();

        assertEquals(1, tools.size());
        assertTrue(stub.isConnected());
        assertEquals(1, stub.reconnectCount.get());
    }

    @Test
    @DisplayName("超过 maxAttempts 抛 McpReconnectExhaustedException")
    void reconnectExhausted() {
        var stub = new AlwaysDisconnectedClient();
        var wrapper = new ReconnectingMcpClient("failing-server", stub, new ReconnectPolicy(1L, 5L, 2, 2.0));

        assertThrows(McpReconnectExhaustedException.class, () -> wrapper.listTools().join());
    }

    @Test
    @DisplayName("重连成功后 isConnected()=true")
    void connectedAfterReconnect() {
        var stub = new StubMcpClient(false);
        var wrapper = new ReconnectingMcpClient("test-server", stub, new ReconnectPolicy(1L, 10L, 3, 2.0));

        assertFalse(wrapper.isConnected());
        wrapper.listTools().join();
        assertTrue(wrapper.isConnected());
    }

    @Test
    @DisplayName("callTool 断连后自动重连")
    void callToolReconnects() {
        var stub = new StubMcpClient(false);
        var wrapper = new ReconnectingMcpClient("test-server", stub, new ReconnectPolicy(1L, 10L, 3, 2.0));

        var result = wrapper.callTool("tool1", Map.of()).join();

        assertNotNull(result);
        assertTrue(stub.isConnected());
    }

    // ── Stubs ─────────────────────────────────────────────────

    private static final class StubMcpClient implements IMcpClient {
        final AtomicInteger reconnectCount = new AtomicInteger(0);
        private volatile boolean connected;

        StubMcpClient(boolean initiallyConnected) {
            this.connected = initiallyConnected;
        }

        @Override public CompletableFuture<Void> connect() {
            reconnectCount.incrementAndGet();
            connected = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletableFuture<Void> disconnect() {
            connected = false;
            return CompletableFuture.completedFuture(null);
        }

        @Override public boolean isConnected() { return connected; }

        @Override public CompletableFuture<List<McpToolDescriptor>> listTools() {
            return CompletableFuture.completedFuture(List.of(new McpToolDescriptor("tool1", "desc", Map.of())));
        }

        @Override public CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpToolResult(List.of(), false));
        }
    }

    private static final class AlwaysDisconnectedClient implements IMcpClient {
        @Override public CompletableFuture<Void> connect() {
            return CompletableFuture.failedFuture(new RuntimeException("cannot connect"));
        }
        @Override public CompletableFuture<Void> disconnect() {
            return CompletableFuture.completedFuture(null);
        }
        @Override public boolean isConnected() { return false; }
        @Override public CompletableFuture<List<McpToolDescriptor>> listTools() {
            return CompletableFuture.failedFuture(new RuntimeException("not connected"));
        }
        @Override public CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return CompletableFuture.failedFuture(new RuntimeException("not connected"));
        }
    }
}
