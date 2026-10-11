package net.linlan.dsh.infrastructure.adapter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.junit.jupiter.api.Test;

class HarnessEffectiveConfigPortTest {

    @Test
    void masksSensitiveKeysAndPreservesOtherValues() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "harness.llm.deepseek.api-key", "secret-value",
                "harness.llm.deepseek.base-url", "http://127.0.0.1:28080/v1",
                "harness.llm.deepseek.max-tokens", 8192,
                "harness.extensions.mcp.servers[0].url",
                "http://example.test/mcp?api_key=super-secret",
                "harness.extensions.skills.roots", List.of("./skills")
        )));
        HarnessEffectiveConfigPort port = new HarnessEffectiveConfigPort(environment);

        Map<String, Object> config = port.effectiveConfig();
        Map<?, ?> llm = asMap(asMap(config.get("llm")).get("deepseek"));
        Map<?, ?> extensions = asMap(config.get("extensions"));
        Map<?, ?> skills = asMap(extensions.get("skills"));

        assertEquals("[MASKED]", llm.get("api-key"));
        assertEquals("http://127.0.0.1:28080/v1", llm.get("base-url"));
        assertEquals(8192, llm.get("max-tokens"));
        Map<?, ?> mcp = asMap(extensions.get("mcp"));
        Map<?, ?> servers = asMap(mcp.get("servers"));
        Map<?, ?> server = asMap(asMap(servers.get("0")));
        assertEquals("http://example.test/mcp?api_key=[MASKED]", server.get("url"));
        assertEquals(List.of("./skills"), skills.get("roots"));
    }

    private static Map<?, ?> asMap(Object value) {
        return (Map<?, ?>) value;
    }
}
