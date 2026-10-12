package net.linlan.dsh.domain.tool.mcp.resource.service;

import net.linlan.dsh.domain.tool.mcp.resource.adapter.port.IMcpResourcePort;
import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.033 McpResourceDiscovery")
class McpResourceDiscoveryTest {

    @Test
    @DisplayName("discover 按 scope 过滤资源")
    void discoverFiltersByScope() {
        StubResourcePort port = new StubResourcePort(List.of(
                new McpResource("file:///a.txt", "a", "desc a", "project"),
                new McpResource("file:///b.txt", "b", "desc b", "global"),
                new McpResource("file:///c.txt", "c", "desc c", "project")
        ));
        McpResourceDiscovery discovery = new McpResourceDiscovery(port);

        List<McpResource> result = discovery.discover("project");

        assertEquals(2, result.size());
        assertEquals("file:///a.txt", result.get(0).uri());
        assertEquals("file:///c.txt", result.get(1).uri());
    }

    @Test
    @DisplayName("discover 空 scope 返回全部资源")
    void discoverEmptyScopeReturnsAll() {
        StubResourcePort port = new StubResourcePort(List.of(
                new McpResource("file:///a.txt", "a", "desc a", "project"),
                new McpResource("file:///b.txt", "b", "desc b", "global")
        ));
        McpResourceDiscovery discovery = new McpResourceDiscovery(port);

        List<McpResource> result = discovery.discover("");

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("discover null scope 返回全部资源")
    void discoverNullScopeReturnsAll() {
        StubResourcePort port = new StubResourcePort(List.of(
                new McpResource("file:///a.txt", "a", "desc a", "project")
        ));
        McpResourceDiscovery discovery = new McpResourceDiscovery(port);

        List<McpResource> result = discovery.discover(null);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("discover 无匹配 scope 返回空列表")
    void discoverNoMatchReturnsEmpty() {
        StubResourcePort port = new StubResourcePort(List.of(
                new McpResource("file:///a.txt", "a", "desc a", "project")
        ));
        McpResourceDiscovery discovery = new McpResourceDiscovery(port);

        List<McpResource> result = discovery.discover("global");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("discover 空资源列表返回空列表")
    void discoverEmptyResourcesReturnsEmpty() {
        StubResourcePort port = new StubResourcePort(List.of());
        McpResourceDiscovery discovery = new McpResourceDiscovery(port);

        List<McpResource> result = discovery.discover("project");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("read 委托给端口读取资源内容")
    void readDelegatesToPort() {
        StubResourcePort port = new StubResourcePort(List.of());
        port.setReadContent("resource content");
        McpResourceDiscovery discovery = new McpResourceDiscovery(port);

        String content = discovery.read("file:///a.txt");

        assertEquals("resource content", content);
        assertEquals("file:///a.txt", port.lastReadUri);
    }

    @Test
    @DisplayName("构造函数 null port 抛 NullPointerException")
    void nullPortThrows() {
        assertThrows(NullPointerException.class, () -> new McpResourceDiscovery(null));
    }

    static class StubResourcePort implements IMcpResourcePort {
        private final List<McpResource> resources;
        private String readContent = "";
        private String lastReadUri;

        StubResourcePort(List<McpResource> resources) {
            this.resources = resources;
        }

        void setReadContent(String content) {
            this.readContent = content;
        }

        @Override
        public List<McpResource> listResources(String scope) {
            return resources;
        }

        @Override
        public String readResource(String uri) {
            this.lastReadUri = uri;
            return readContent;
        }
    }
}
