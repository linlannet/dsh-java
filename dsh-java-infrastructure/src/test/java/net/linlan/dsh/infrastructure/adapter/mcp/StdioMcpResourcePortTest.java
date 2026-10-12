package net.linlan.dsh.infrastructure.adapter.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.033 StdioMcpResourcePort stdio MCP 资源端口适配器")
class StdioMcpResourcePortTest {

    private static final String MCP_STUB_SCRIPT = """
            import json, sys
            for line in sys.stdin:
                req = json.loads(line)
                method = req.get("method")
                rid = req.get("id")
                if method == "resources/list":
                    result = {"resources": [{"uri": "file:///a", "name": "A", "description": "desc-a"}]}
                elif method == "resources/read":
                    result = {"contents": [{"text": "resource-content"}]}
                else:
                    result = {}
                print(json.dumps({"jsonrpc": "2.0", "id": rid, "result": result}), flush=True)
            """;

    private static boolean python3Available() {
        try {
            Process p = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static StdioMcpResourcePort newStubPort() {
        return new StdioMcpResourcePort("python3", List.of("-u", "-c", MCP_STUB_SCRIPT));
    }

    @Test
    @DisplayName("构造函数 null args 转为空列表")
    void constructorNullArgsBecomesEmpty() {
        StdioMcpResourcePort port = new StdioMcpResourcePort("cmd", null);
        assertTrue(port != null);
    }

    @Test
    @DisplayName("listResources 未连接抛 IllegalStateException")
    void listResourcesNotConnectedThrows() {
        StdioMcpResourcePort port = newStubPort();
        assertThrows(IllegalStateException.class, () -> port.listResources("scope"));
    }

    @Test
    @DisplayName("readResource 未连接抛 IllegalStateException")
    void readResourceNotConnectedThrows() {
        StdioMcpResourcePort port = newStubPort();
        assertThrows(IllegalStateException.class, () -> port.readResource("file:///a"));
    }

    @Test
    @DisplayName("connect 后 listResources 返回资源列表")
    void connectThenListResourcesReturnsResources() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioMcpResourcePort port = newStubPort();
        port.connect();
        try {
            List<McpResource> resources = port.listResources("scope1");
            assertEquals(1, resources.size());
            assertEquals("file:///a", resources.get(0).uri());
            assertEquals("A", resources.get(0).name());
            assertEquals("desc-a", resources.get(0).description());
            assertEquals("scope1", resources.get(0).scope());
        } finally {
            port.disconnect();
        }
    }

    @Test
    @DisplayName("connect 后 readResource 返回资源内容")
    void connectThenReadResourceReturnsContent() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioMcpResourcePort port = newStubPort();
        port.connect();
        try {
            String content = port.readResource("file:///a");
            assertEquals("resource-content", content);
        } finally {
            port.disconnect();
        }
    }

    @Test
    @DisplayName("disconnect 后 listResources 抛 IllegalStateException")
    void disconnectThenListResourcesThrows() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioMcpResourcePort port = newStubPort();
        port.connect();
        port.disconnect();
        assertThrows(IllegalStateException.class, () -> port.listResources("scope"));
    }

    @Test
    @DisplayName("listResources 空 scope 不抛异常")
    void listResourcesEmptyScopeDoesNotThrow() throws Exception {
        Assumptions.assumeTrue(python3Available());
        StdioMcpResourcePort port = newStubPort();
        port.connect();
        try {
            List<McpResource> resources = port.listResources("");
            assertEquals(1, resources.size());
            assertEquals("", resources.get(0).scope());
        } finally {
            port.disconnect();
        }
    }

    @Test
    @DisplayName("StdioMcpResourcePort 实现 IMcpResourcePort")
    void implementsIMcpResourcePort() {
        StdioMcpResourcePort port = newStubPort();
        assertTrue(port instanceof net.linlan.dsh.domain.tool.mcp.resource.adapter.port.IMcpResourcePort);
    }
}
