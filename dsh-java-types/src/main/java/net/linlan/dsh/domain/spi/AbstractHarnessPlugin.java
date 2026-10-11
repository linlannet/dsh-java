package net.linlan.dsh.domain.spi;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience base class for {@link JavaHarnessPlugin} implementations.
 * <p>
 * Subclasses only need to:
 * <ul>
 *   <li>pass the plugin id to {@link #AbstractHarnessPlugin(String)}</li>
 *   <li>override {@link #tools()} and return tool instances</li>
 *   <li>optionally override {@link #onStart()} / {@link #onStop()}</li>
 * </ul>
 * The base class wires a named {@link Logger} and a default {@link #pluginId()}
 * backed by the constructor argument, so the id cannot drift from the value
 * declared in {@code META-INF/plugin.yaml}.
 *
 * <pre>{@code
 * public class MyPlugin extends AbstractHarnessPlugin {
 *     public MyPlugin() { super("my-plugin"); }
 *
 *     @Override
 *     public List<ToolDefinition> tools() {
 *         return List.of(new MyTool());
 *     }
 * }
 * }</pre>
 *
 * <p><b>完整工程结构</b></p>
 * <pre>
 * my-plugin/
 * ├── pom.xml                          ← 依赖 types，scope=provided
 * └── src/main/
 *     ├── java/com/example/
 *     │   ├── MyPlugin.java            ← 继承 AbstractHarnessPlugin
 *     │   └── MyTool.java              ← 继承 AbstractTool
 *     └── resources/META-INF/
 *         ├── plugin.yaml              ← id/name/version/entrypoint
 *         └── services/
 *             └── ...JavaHarnessPlugin ← 内容: com.example.MyPlugin
 * </pre>
 *
 * <p>plugin.yaml 里 entrypoint 填 com.example.MyPlugin，或省略走 ServiceLoader。
 * <p>打 JAR 后放入 harness 的 plugins/ 目录，配置 preset 或调 HTTP API 加载。
 */
public abstract class AbstractHarnessPlugin implements JavaHarnessPlugin {

    private final String pluginId;
    protected final Logger log;

    /**
     * @param pluginId 稳定的插件 ID，必须与 {@code META-INF/plugin.yaml}
     *                 中的 {@code id} 字段一致
     */
    protected AbstractHarnessPlugin(String pluginId) {
        this.pluginId = pluginId;
        this.log = LoggerFactory.getLogger(getClass());
    }

    @Override
    public final String pluginId() {
        return pluginId;
    }

    @Override
    public void onStart() {
        log.info("[{}] plugin started", pluginId);
    }

    /**
     * 插件停止回调，默认空实现。
     * 子类可在此释放运行时资源。
     */
    @Override
    public void onStop() {
        log.info("[{}] plugin stopped", pluginId);
    }

    /**
     * 收集工具并过滤 {@code null} 项。
     * 子类通常直接 {@code return List.of(...)}；只有动态工具集时才需要覆写。
     */
    @Override
    public List<ToolDefinition> tools() {
        return List.of();
    }

    /**
     * Optional hook for plugins that need to register resources in addition to
     * the tools returned by {@link #tools()}.
     */
    @Override
    public void configure(PluginContext context) {
        JavaHarnessPlugin.super.configure(context);
    }

    /**
     * 辅助动态插件逐步构建工具列表。
     */
    protected static List<ToolDefinition> toolList(ToolDefinition... tools) {
        List<ToolDefinition> list = new ArrayList<>(tools.length);
        for (ToolDefinition t : tools) {
            if (t != null) list.add(t);
        }
        return List.copyOf(list);
    }
}
