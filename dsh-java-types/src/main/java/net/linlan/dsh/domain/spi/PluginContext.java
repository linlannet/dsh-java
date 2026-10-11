package net.linlan.dsh.domain.spi;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.spi.PluginEventHandler;
import net.linlan.dsh.domain.spi.PluginHook;

/**
 * Java 插件加载期间获得的稳定注册边界。
 * <p>插件通过该上下文注册工具、监听事件、挂载 Hook、读取配置和注入提示词。
 * 所有注册项都由宿主持有；插件停止、加载失败或卸载时，宿主会逆序回收。
 * <p>数据示例：插件 id="time" 注册工具 now、事件 greeting 和 PRE_TOOL_USE Hook；
 * 停止时工具、监听器和 Hook 会全部注销。
 */
public interface PluginContext {

    /**
     * 返回插件登记 ID。
     */
    String pluginId();

    /**
     * 注册一个工具。
     * <p>工具名会被宿主限定为 {@literal plugin__<pluginId>__<toolName>}；
     * 返回的关闭器由宿主管理，并在停止时逆序调用。
     */
    AutoCloseable registerTool(ToolDefinition tool);

    /**
     * 注册必须在关闭插件 ClassLoader 前释放的资源。
     * <p>典型场景包括数据库连接、定时器和文件监听器。
     */
    void registerDisposer(AutoCloseable disposer);

    /**
     * 在插件事件总线上订阅指定名称的事件。
     * <p>返回的关闭器由宿主持有，插件停止时自动取消订阅。
     */
    AutoCloseable subscribe(String eventName, PluginEventHandler handler);

    /**
     * 发布一个“发送后即忘”的插件事件。
     * <p>事件源固定为当前 pluginId；单个处理器失败不会阻断其他处理器。
     */
    void emit(String eventName, Object payload);

    /**
     * 在指定 Hook 点注册进程内回调。
     * <p>hookPoint 必须匹配宿主 HookPoint 枚举名称，例如 PRE_TOOL_USE、POST_TOOL_USE、
     * USER_PROMPT_SUBMIT、SESSION_START、STOP 或 NOTIFICATION。
     * PRE_TOOL_USE 返回 BLOCK/DENY 会阻止工具执行；POST_TOOL_USE 只用于观察。
     */
    AutoCloseable registerHook(String hookPoint, PluginHook hook);

    /**
     * 解析当前插件的配置值；键不存在时返回空 Optional。
     */
    java.util.Optional<String> getConfig(String key);

    /**
     * 解析当前插件配置；不存在时返回 defaultValue。
     */
    default String getConfig(String key, String defaultValue) {
        return getConfig(key).orElse(defaultValue);
    }

    /**
     * 注册插件贡献的系统提示词段落。
     * <p>段落名自动加 {@literal plugin__<pluginId>__} 前缀，避免插件之间冲突；插件停止时移除。
     *
     * @param name  不含插件前缀的短名称
     * @param order 排序值，数字越小越靠前
     * @param text  提示词内容
     */
    AutoCloseable registerSystemPrompt(String name, int order, String text);
}
