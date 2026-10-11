package net.linlan.dsh.infrastructure.config;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * Validates the standard harness configuration before the application serves requests.
 */
@Component
public class HarnessConfigValidator implements InitializingBean {

    private static final Set<String> SANDBOX_MODES = Set.of(
            "READ_ONLY", "WORKSPACE_WRITE", "DANGER_FULL_ACCESS"
    );
    private static final ResolvableType SERVER_LIST_TYPE = ResolvableType.forClassWithGenerics(
            List.class, Map.class
    );

    private final ConfigurableEnvironment environment;

    public HarnessConfigValidator(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validateSandbox();
        validateMcpServers();
    }

    private void validateSandbox() {
        String mode = Binder.get(environment)
                .bind("harness.sandbox.default-mode", String.class)
                .orElse("READ_ONLY");
        String normalized = mode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!SANDBOX_MODES.contains(normalized)) {
            throw new IllegalStateException("harness.sandbox.default-mode must be one of " + SANDBOX_MODES);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> mcpServers() {
        Object serverListValue = Binder.get(environment)
                .bind("harness.extensions.mcp.servers", Bindable.of(SERVER_LIST_TYPE))
                .orElseGet(List::of);
        List<?> serverList = (List<?>) serverListValue;
        return serverList.stream()
                .map(value -> (Map<String, Object>) (Map<?, ?>) value)
                .toList();
    }

    private void validateMcpServers() {
        Set<String> names = new HashSet<>();
        List<Map<String, Object>> servers = mcpServers();
        for (int index = 0; index < servers.size(); index++) {
            Map<String, Object> server = servers.get(index);
            String path = "harness.extensions.mcp.servers[" + index + "]";
            String name = text(server.get("name"));
            if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
                throw new IllegalStateException(path + ".name must match [A-Za-z0-9_-]{1,32}");
            }
            if (!names.add(name)) {
                throw new IllegalStateException("duplicate MCP server name: " + name);
            }

            String transport = normalizeTransport(text(server.getOrDefault("transport", "stdio")), path);
            if ("stdio".equals(transport)) {
                requireNotBlank(text(server.get("command")), path + ".command is required for stdio transport");
            } else {
                String url = requireNotBlank(text(server.get("url")), path + ".url is required for " + transport);
                URI uri = URI.create(url);
                if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                    throw new IllegalStateException(path + ".url must use http or https");
                }
            }
        }
    }

    private String normalizeTransport(String transport, String path) {
        String normalized = transport.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stdio" -> "stdio";
            case "sse" -> "sse";
            case "http", "streamable_http", "streamable-http" -> "streamable-http";
            default -> throw new IllegalStateException(path + ".transport is not supported: " + transport);
        };
    }

    private String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
