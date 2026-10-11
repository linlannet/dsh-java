package net.linlan.dsh.domain.tool.mcp;

import java.util.List;
import java.util.Map;

/**
 * 一个 MCP server 的连接配置。
 *
 * @param name    server 名（工具名前缀 mcp__&lt;name&gt;__*）
 * @param command 可执行命令（stdio 传输）
 * @param args    命令参数
 * @param env     追加环境变量
 * @param cwd     工作目录（可空）
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public record McpServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env,
        String cwd,
        String transport,
        String url,
        Map<String, String> headers
) {
    public McpServerConfig {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        transport = normalizeTransport(transport);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        if ("stdio".equals(transport) && (command == null || command.isBlank())) {
            throw new IllegalArgumentException("stdio MCP transport requires command");
        }
        if (!"stdio".equals(transport) && (url == null || url.isBlank())) {
            throw new IllegalArgumentException(transport + " MCP transport requires url");
        }
    }

    public McpServerConfig(
            String name,
            String command,
            List<String> args,
            Map<String, String> env,
            String cwd
    ) {
        this(name, command, args, env, cwd, "stdio", null, Map.of());
    }

    private static String normalizeTransport(String transport) {
        String normalized = transport == null || transport.isBlank() ? "stdio" : transport.trim().toLowerCase();
        return switch (normalized) {
            case "http", "streamable_http", "streamable-http" -> "streamable-http";
            case "sse", "stdio" -> normalized;
            default -> throw new IllegalArgumentException("unsupported MCP transport: " + transport);
        };
    }
}
