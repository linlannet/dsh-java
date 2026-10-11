package net.linlan.dsh.domain.tool.mcp;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class McpServerConfigTest {

    @Test
    void defaultsMissingTransportToStdio() {
        McpServerConfig config = new McpServerConfig(
                "local", "node", List.of("server.js"), Map.of(), null);

        assertEquals("stdio", config.transport());
    }

    @Test
    void normalizesStreamableHttpAliases() {
        McpServerConfig config = new McpServerConfig(
                "remote", null, List.of(), Map.of(), null,
                "Streamable_HTTP", "http://127.0.0.1/mcp", Map.of());

        assertEquals("streamable-http", config.transport());
    }

    @Test
    void rejectsUnsupportedTransport() {
        assertThrows(IllegalArgumentException.class, () -> new McpServerConfig(
                "remote", null, List.of(), Map.of(), null, "websocket", "http://127.0.0.1/mcp", Map.of()));
    }

    @Test
    void requiresCommandForStdio() {
        assertThrows(IllegalArgumentException.class, () -> new McpServerConfig(
                "local", null, List.of(), Map.of(), null, "stdio", null, Map.of()));
    }

    @Test
    void requiresUrlForHttpTransports() {
        assertThrows(IllegalArgumentException.class, () -> new McpServerConfig(
                "remote", null, List.of(), Map.of(), null, "sse", null, Map.of()));
    }
}
