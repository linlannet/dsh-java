package net.linlan.dsh.domain.tool.mcp;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.typert.model.valobj.PtcProgramOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
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

    @Test
    @DisplayName("MCP 图片转为附件引用和程序 Image 值，不透传 base64 JSON")
    void imageBlocksBecomeAttachmentReferencesAndProgramImages() {
        byte[] data = {1, 2, 3, 4};
        String base64 = Base64.getEncoder().encodeToString(data);
        var client = new NoopClient(new IMcpClient.McpToolResult(List.of(
                Map.of("type", "text", "text", "before"),
                Map.of("type", "image", "mimeType", "image/png", "data", base64),
                Map.of("type", "text", "text", "after")
        ), false));
        var descriptor = new IMcpClient.McpToolDescriptor("screenshot", "Screenshot", Map.of());
        var adapter = new McpToolAdapter("server", descriptor, client, 60_000L);

        ToolExecutionResult.Success result = (ToolExecutionResult.Success) adapter.execute(Map.of(), null).join();

        assertFalse(result.isError());
        assertEquals(3, result.content().size());
        ImageBlock modelImage = assertInstanceOf(ImageBlock.class, result.content().get(1));
        ImageAttachmentRef attachment = modelImage.attachment();
        assertArrayEquals(data, attachment.data());
        assertEquals("image/png", attachment.mimeType());

        Map<?, ?> programValue = assertInstanceOf(Map.class, result.value());
        List<?> programContent = assertInstanceOf(List.class, programValue.get("content"));
        ImageAttachmentRef programImage = assertInstanceOf(ImageAttachmentRef.class, programContent.get(1));
        assertSame(attachment, programImage);
        assertFalse(programValue.toString().contains(base64));

        PtcProgramOutput output = new PtcProgramOutput();
        output.inspect(programImage);
        ImageBlock inspected = assertInstanceOf(ImageBlock.class, output.blocks().get(0));
        assertSame(attachment, inspected.attachment());
    }

    private static final class NoopClient implements IMcpClient {
        private final McpToolResult result;

        private NoopClient() {
            this(new McpToolResult(List.of(), false));
        }

        private NoopClient(McpToolResult result) {
            this.result = result;
        }

        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> disconnect() { return CompletableFuture.completedFuture(null); }
        @Override public boolean isConnected() { return true; }
        @Override public CompletableFuture<List<McpToolDescriptor>> listTools() { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<McpToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(result);
        }
    }
}
