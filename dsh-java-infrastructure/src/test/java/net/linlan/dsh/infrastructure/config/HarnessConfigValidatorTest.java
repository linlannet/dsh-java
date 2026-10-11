package net.linlan.dsh.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class HarnessConfigValidatorTest {

    @Test
    void acceptsEmptyMcpServersAndValidSandboxMode() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "harness.sandbox.default-mode", "workspace-write"
        )));

        assertDoesNotThrow(() -> new HarnessConfigValidator(environment).afterPropertiesSet());
    }

    @Test
    void rejectsDuplicateMcpServerNames() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.ofEntries(
                Map.entry("harness.extensions.mcp.servers[0].name", "search"),
                Map.entry("harness.extensions.mcp.servers[0].transport", "sse"),
                Map.entry("harness.extensions.mcp.servers[0].url", "http://example.test/sse"),
                Map.entry("harness.extensions.mcp.servers[1].name", "search"),
                Map.entry("harness.extensions.mcp.servers[1].transport", "sse"),
                Map.entry("harness.extensions.mcp.servers[1].url", "http://example.test/sse")
        )));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new HarnessConfigValidator(environment).afterPropertiesSet());
        assertTrue(exception.getMessage().contains("duplicate MCP server name"));
    }

    @Test
    void rejectsStreamableHttpWithoutUrl() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "harness.extensions.mcp.servers[0].name", "remote",
                "harness.extensions.mcp.servers[0].transport", "streamable-http"
        )));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new HarnessConfigValidator(environment).afterPropertiesSet());
        assertTrue(exception.getMessage().contains(".url is required"));
    }
}
