package net.linlan.dsh.domain.tool.plugin;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * PluginToolDefinition 表示PluginToolDefinition。
 */
public class PluginToolDefinition implements ToolDefinition {

    private final String pluginId;
    private final IPluginToolBridge bridge;
    private final IPluginToolBridge.PluginToolDescriptor descriptor;

    /**
     * 创建插件工具定义。
     * <p>用途：把插件提供的工具转换成 Harness 通用工具目录条目。</p>
     */
    public PluginToolDefinition(
            String pluginId,
            IPluginToolBridge bridge,
            IPluginToolBridge.PluginToolDescriptor descriptor) {
        this.pluginId = pluginId;
        this.bridge = bridge;
        this.descriptor = descriptor;
    }

    @Override
    public String name() {
        return "plugin__" + pluginId + "__" + descriptor.name();
    }
    /**
     * 返回 description。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public String description() {
        return descriptor.description() != null ? descriptor.description() : "Plugin tool: " + descriptor.name();
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

        return bridge.callTool(pluginId, descriptor.name(), argMap)
                .thenApply(result -> {
                    if (result.isError()) {
                        return ToolExecutionResult.fail(
                                result.errorText() != null ? result.errorText() : "Plugin tool error",
                                "PLUGIN_ERROR");
                    }

                    List<ContentBlock> blocks = result.content();
                    if (blocks == null || blocks.isEmpty()) {
                        blocks = List.of(new TextBlock("[Plugin tool returned no content]"));
                    }

                    return (ToolExecutionResult) ToolExecutionResult.ok(blocks);
                })
                .exceptionally(e -> ToolExecutionResult.fail(
                        "Plugin tool call failed: " + e.getMessage(), "PLUGIN_CALL_ERROR"));
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
}
