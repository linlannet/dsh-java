package net.linlan.dsh.domain.tool.plugin;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code plugin_status} 工具：查询插件登记状态和运行时状态。
 * <p>运行状态必须来自运行时适配器，不能根据数据库登记状态推断。</p>
 */
public final class PluginStatusTool implements ToolDefinition {

    private final IHarnessPluginRepository pluginRepository;
    private final IPluginRuntimeService pluginRuntimeService;

    public PluginStatusTool(
            IHarnessPluginRepository pluginRepository,
            IPluginRuntimeService pluginRuntimeService
    ) {
        this.pluginRepository = pluginRepository;
        this.pluginRuntimeService = pluginRuntimeService;
    }

    @Override
    public String name() {
        return "plugin_status";
    }

    @Override
    public String description() {
        return "List installed plugins and their authoritative registry and runtime status. "
                + "Use this tool before saying that a plugin is installed, enabled, or running.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "pluginId", Map.of(
                                "type", "string",
                                "description", "Optional plugin id. Omit it to list all installed plugins."
                        )
                )
        );
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext context) {
        String requestedPluginId = args instanceof Map<?, ?> arguments
                && arguments.get("pluginId") instanceof String value && !value.isBlank()
                ? value : null;

        try {
            List<String> lines = pluginRepository.listAll().stream()
                    .filter(plugin -> requestedPluginId == null
                            || requestedPluginId.equals(plugin.getPluginId()))
                    .map(this::format)
                    .toList();

            if (requestedPluginId != null && lines.isEmpty()) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail(
                        "Plugin is not installed: " + requestedPluginId, "PLUGIN_NOT_FOUND"));
            }
            String output = lines.isEmpty() ? "No plugins are installed." : String.join("\n", lines);
            return CompletableFuture.completedFuture(ToolExecutionResult.ok(List.of(new TextBlock(output))));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(
                    "plugin_status failed: " + exception.getMessage(), "EXECUTION_ERROR"));
        }
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return true;
    }

    private String format(net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity plugin) {
        var runtimeStatus = pluginRuntimeService.status(plugin.getPluginId());
        return "- pluginId=" + plugin.getPluginId()
                + ", displayName=" + plugin.getDisplayName()
                + ", registryStatus=" + plugin.getStatus()
                + ", running=" + runtimeStatus.running()
                + ", runtimeCommand=" + runtimeStatus.runtimeCommand()
                + ", message=" + runtimeStatus.message();
    }
}
