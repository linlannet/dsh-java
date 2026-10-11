package ${package};

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.spi.AbstractHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginContext;
import net.linlan.dsh.domain.spi.PluginHookResult;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ${pluginName} — ${pluginDescription}
 * <p>
 * 继承 {@link AbstractHarnessPlugin}，只需传 pluginId 并实现 {@link #tools()}。
 * pluginId 必须与 META-INF/plugin.yaml 的 id 字段一致。
 */
public class ${pluginClass} extends AbstractHarnessPlugin {

    private static final Logger log = LoggerFactory.getLogger(${pluginClass}.class);

    public ${pluginClass}() {
        super("${pluginId}");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new ExampleTool());
    }
    /**
     * configure。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param context 上下文对象。
     */


    @Override
    public void configure(PluginContext context) {
        super.configure(context);

        // 注册系统提示词段落
        context.registerSystemPrompt("about", 10,
                String.join(System.lineSeparator(), "Plugin: ${pluginName}", "- 插件描述：${pluginDescription}"));

        // 订阅事件
        context.subscribe("tool.called", event ->
                log.info("[${pluginId}] received event: {}", event.name()));

        // 注册 PRE_TOOL_USE hook
        context.registerHook("PRE_TOOL_USE", (toolName, payload) -> {
            boolean auditEnabled = context.getConfig("audit-enabled", "true")
                    .equalsIgnoreCase("true");
            if (!auditEnabled || !"shell_execute".equals(toolName)) return null;
            log.info("[${pluginId}] PRE_TOOL_USE audit: {}", toolName);
            return PluginHookResult.context("Audited by ${pluginId}.");
        });

        // 发布自定义事件
        context.emit("plugin.ready", Map.of("pluginId", "${pluginId}"));
    }
}
