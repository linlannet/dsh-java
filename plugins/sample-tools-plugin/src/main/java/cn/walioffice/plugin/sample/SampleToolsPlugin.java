package cn.walioffice.plugin.sample;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.spi.AbstractHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginContext;
import net.linlan.dsh.domain.spi.PluginHookResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例插件入口：提供天气查询和文件摘要两个工具。
 * <p>
 * 继承 {@link AbstractHarnessPlugin}，只需传 pluginId 并实现 {@link #tools()}。
 * 通过 application.yml preset 预装，启动即可用。
 *
 * <p>调用流程：接收输入 → 执行本类职责 → 返回结果。</p>
 * <p>示例：根据调用方传入参数完成一次独立操作。</p>
 * 
 */
public class SampleToolsPlugin extends AbstractHarnessPlugin {

    private static final Logger log = LoggerFactory.getLogger(SampleToolsPlugin.class);

    private final AtomicInteger hookInvocationCount = new AtomicInteger();

    public SampleToolsPlugin() {
        super("sample-tools");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new WeatherQueryTool(), new FileSummaryTool());
    }
    /**
     * configure。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param context 上下文对象。
     */


    @Override
    public void configure(PluginContext context) {
        super.configure(context);

        context.registerSystemPrompt("capabilities", 10,
                "## Plugin capabilities\n- sample-tools provides weather_query and file_summary tools.\n"
                + "- A PRE_TOOL_USE hook audits every shell_execute call.\n"
                + "- Plugin config key 'audit-enabled' controls hook behavior.");

        context.subscribe("tool.called", event ->
                log.info("[sample-tools] received event: source={}, payload={}",
                        event.source(), event.payload()));

        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (!"shell_execute".equals(toolName)) {
                return null;
            }
            boolean auditEnabled = context.getConfig("audit-enabled", "true")
                    .equalsIgnoreCase("true");
            if (!auditEnabled) {
                return null;
            }
            int count = hookInvocationCount.incrementAndGet();
            log.info("[sample-tools] PRE_TOOL_USE audit #{}, tool={}, payload={}",
                    count, toolName, payloadJson);
            return PluginHookResult.context(
                    "Audit notice: shell_execute call #" + count + " audited by sample-tools plugin.");
        });

        context.registerHook("POST_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__sample-tools__")) {
                context.emit("tool.called", Map.of(
                        "tool", toolName,
                        "payload", payloadJson == null ? "" : payloadJson
                ));
            }
            return null;
        });

        String greeting = context.getConfig("greeting", "Hello from sample-tools!");
        log.info("[sample-tools] configure() called, greeting='{}'", greeting);
        context.emit("plugin.custom-event", greeting);
    }
}
