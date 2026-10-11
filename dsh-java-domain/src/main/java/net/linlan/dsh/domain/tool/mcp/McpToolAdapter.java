package net.linlan.dsh.domain.tool.mcp;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * McpToolAdapter 表示McpToolAdapter。
 */
public class McpToolAdapter implements ToolDefinition {

    private final String serverName;
    private final IMcpClient.McpToolDescriptor descriptor;
    private final IMcpClient mcpClient;
    private final long timeoutMs;

    /**
     * 处理MCP 工具 Adapter。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param serverName server 名称参数。
     * @param descriptor descriptor参数。
     * @param mcpClient MCP Client参数。
     * @param timeoutMs 超时时间，毫秒。
     * @return 方法执行结果。
     */
    public McpToolAdapter(String serverName, IMcpClient.McpToolDescriptor descriptor, IMcpClient mcpClient, long timeoutMs) {
        this.serverName = serverName;
        this.descriptor = descriptor;
        this.mcpClient = mcpClient;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String name() {
        return "mcp__" + serverName + "__" + descriptor.name();
    }
    /**
     * 返回 description。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public String description() {
        return descriptor.description() != null ? descriptor.description() : "MCP tool: " + descriptor.name();
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return descriptor.inputSchema() != null ? descriptor.inputSchema() : Map.of("type", "object");
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "additionalProperties", true);
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        Map<String, Object> argMap = args instanceof Map ? (Map<String, Object>) args : Map.of();

        return mcpClient.callTool(descriptor.name(), argMap)
                .thenApply(result -> {
                    if (result.isError()) {
                        String errorText = result.content().stream()
                                .filter(c -> "text".equals(c.get("type")))
                                .map(c -> String.valueOf(c.get("text")))
                                .reduce("", (a, b) -> a + b);
                        return ToolExecutionResult.fail(
                                errorText.isEmpty() ? "MCP tool error" : errorText,
                                "MCP_ERROR");
                    }

                    List<ContentBlock> blocks = new ArrayList<>();
                    for (Map<String, Object> c : result.content()) {
                        if ("text".equals(c.get("type"))) {
                            blocks.add(new TextBlock(String.valueOf(c.get("text"))));
                        }
                    }

                    if (blocks.isEmpty()) {
                        blocks.add(new TextBlock("[MCP tool returned no text content]"));
                    }

                    return (ToolExecutionResult) ToolExecutionResult.ok(blocks);
                })
                .exceptionally(e -> ToolExecutionResult.fail(
                        "MCP call failed: " + e.getMessage(), "MCP_CALL_ERROR"));
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
    /**
     * 返回 timeout Ms。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public long timeoutMs() { return timeoutMs; }
}
