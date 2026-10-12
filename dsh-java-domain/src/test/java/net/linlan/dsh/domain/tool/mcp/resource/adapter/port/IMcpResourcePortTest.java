package net.linlan.dsh.domain.tool.mcp.resource.adapter.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.033 IMcpResourcePort 端口契约")
class IMcpResourcePortTest {

    @Test
    @DisplayName("listResources(scope) 返回资源列表")
    void listResourcesReturnsResources() {
        StubPort port = new StubPort();
        port.resources = List.of(new McpResource("file:///a", "a", "desc", "scope1"));
        List<McpResource> result = port.listResources("scope1");
        assertEquals(1, result.size());
        assertEquals("file:///a", result.get(0).uri());
    }

    @Test
    @DisplayName("listResources 空 scope 返回空列表")
    void listResourcesEmptyScopeReturnsEmpty() {
        StubPort port = new StubPort();
        List<McpResource> result = port.listResources("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("readResource(uri) 返回资源内容")
    void readResourceReturnsContent() {
        StubPort port = new StubPort();
        port.content = "resource-content";
        String result = port.readResource("file:///a");
        assertEquals("resource-content", result);
    }

    @Test
    @DisplayName("实现类实例是 IMcpResourcePort 子类型")
    void implementationIsPortSubtype() {
        IMcpResourcePort port = new StubPort();
        assertNotNull(port);
        assert IMcpResourcePort.class.isInstance(port);
    }

    static final class StubPort implements IMcpResourcePort {
        List<McpResource> resources = List.of();
        String content = "";

        @Override
        public List<McpResource> listResources(String scope) {
            return resources;
        }

        @Override
        public String readResource(String uri) {
            return content;
        }
    }
}
