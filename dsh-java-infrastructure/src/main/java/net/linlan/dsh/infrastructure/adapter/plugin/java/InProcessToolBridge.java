package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecution;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.tool.plugin.IPluginToolBridge;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts an in-process {@link ToolDefinition} (loaded from a Java plugin JAR)
 * to the {@link IPluginToolBridge} contract so that {@link PluginToolDefinition}
 * can wrap it uniformly.
 * <p>
 * Unlike {@code JsonRpcPluginToolBridge} which talks to a Node sidecar over
 * stdio, this bridge delegates directly to the in-JVM tool implementation —
 * no serialization, no subprocess.
 *
 *
 */
class InProcessToolBridge implements IPluginToolBridge {

    private final ToolDefinition delegate;

    InProcessToolBridge(ToolDefinition delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> connect(String pluginId, Process process) {
        return CompletableFuture.completedFuture(null);
    }
    /**
     * disconnect。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<Void> disconnect(String pluginId) {
        return CompletableFuture.completedFuture(null);
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConnected(String pluginId) {
        return true;
    }
    /**
     * 列出Tools。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<List<PluginToolDescriptor>> listTools(String pluginId) {
        return CompletableFuture.completedFuture(List.of(
                new PluginToolDescriptor(delegate.name(), delegate.description(), delegate.parameters())
        ));
    }

    @Override
    /**
     * call Tool。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @param toolName tool Name参数。
     * @param Map<String Map String参数。
     * @param arguments arguments参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<PluginToolResult> callTool(String pluginId, String toolName, Map<String, Object> arguments) {
        ToolExecution exec = new ToolExecution(
                java.util.UUID.randomUUID().toString(),
                java.util.UUID.randomUUID().toString(),
                toolName,
                arguments,
                java.util.UUID.randomUUID().toString()
        );
        ToolRunContext ctx = new ToolRunContext(exec);

        return delegate.execute(arguments, ctx)
                .thenApply(result -> {
                    List<ContentBlock> content = result.content();
                    if (content == null || content.isEmpty()) {
                        content = List.of(new TextBlock("[tool returned no content]"));
                    }
                    if (result.isError()) {
                        ToolExecutionResult.Failure failure = (ToolExecutionResult.Failure) result;
                        String errorText = failure.error() != null ? failure.error().message() : "tool error";
                        return new PluginToolResult(content, true, errorText);
                    }
                    return new PluginToolResult(content, false, null);
                })
                .exceptionally(e -> new PluginToolResult(
                        List.of(new TextBlock("tool call failed: " + e.getMessage())),
                        true, e.getMessage()));
    }
}
