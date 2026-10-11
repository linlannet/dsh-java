package net.linlan.dsh.domain.tool.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T1.032 McpToolAdapter 强制命名规范")
class McpToolAdapterTest {

    @Test
    @DisplayName("工具名格式为 mcp__{server}__{tool}")
    void namingFormat() {
        var descriptor = new IMcpClient.McpToolDescriptor("read_file", "Read a file", Map.of());
        var adapter = new McpToolAdapter("filesystem", descriptor, new NoopClient(), 60_000L);

        assertEquals("mcp__filesystem__read_file", adapter.name());
    }

    @Test
    @DisplayName("两个 server 同名工具因前缀不同无冲突")
    void noConflictBetweenServers() {
        var descriptor = new IMcpClient.McpToolDescriptor("search", "Search", Map.of());
        var adapterA = new McpToolAdapter("server-a", descriptor, new NoopClient(), 60_000L);
        var adapterB = new McpToolAdapter("server-b", descriptor, new NoopClient(), 60_000L);

        assertNotEquals(adapterA.name(), adapterB.name());
        assertEquals("mcp__server-a__search", adapterA.name());
        assertEquals("mcp__server-b__search", adapterB.name());
    }

    @Test
    @DisplayName("同一 server 不同工具名无冲突")
    void noConflictWithinSameServer() {
        var desc1 = new IMcpClient.McpToolDescriptor("read", "Read", Map.of());
        var desc2 = new IMcpClient.McpToolDescriptor("write", "Write", Map.of());
        var adapter1 = new McpToolAdapter("fs", desc1, new NoopClient(), 60_000L);
        var adapter2 = new McpToolAdapter("fs", desc2, new NoopClient(), 60_000L);

        assertNotEquals(adapter1.name(), adapter2.name());
    }

    @Test
    @DisplayName("description 为 null 时回退到默认描述")
    void nullDescriptionFallback() {
        var descriptor = new IMcpClient.McpToolDescriptor("tool1", null, Map.of());
        var adapter = new McpToolAdapter("server", descriptor, new NoopClient(), 60_000L);

        assertEquals("MCP tool: tool1", adapter.description());
    }

    @Test
    @DisplayName("inputSchema 为 null 时回退到默认 schema")
    void nullSchemaFallback() {
        var descriptor = new IMcpClient.McpToolDescriptor("tool1", "desc", null);
        var adapter = new McpToolAdapter("server", descriptor, new NoopClient(), 60_000L);

        assertNotNull(adapter.parameters());
        assertEquals("object", adapter.parameters().get("type"));
    }

    private static final class NoopClient implements IMcpClient {
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> disconnect() { return CompletableFuture.completedFuture(null); }
        @Override public boolean isConnected() { return true; }
        @Override public CompletableFuture<List<McpToolDescriptor>> listTools() { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpToolResult(List.of(), false));
        }
    }
}
