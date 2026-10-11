package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.spi.PluginContext;
import net.linlan.dsh.domain.spi.PluginEvent;
import net.linlan.dsh.domain.spi.PluginEventBus;
import net.linlan.dsh.domain.spi.PluginEventHandler;
import net.linlan.dsh.domain.spi.PluginHook;
import net.linlan.dsh.domain.spi.PluginConfigStore;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.plugin.IPluginToolBridge;
import net.linlan.dsh.domain.tool.plugin.PluginToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring 宿主组件实现的插件上下文。
 * <p>上下文是插件贡献项的事务边界：工具、事件订阅、Hook、提示词段落和自定义释放器
 * 都先加入 registrations；close 时逆序关闭，保证启动失败可以回滚到未注册状态。
 * <p>数据示例：插件依次注册 tool-a、tool-b 和事件订阅，close 时先取消订阅，
 * 再注销 tool-b，最后注销 tool-a。
 */
final class SpringPluginContext implements PluginContext, AutoCloseable {

    private final String pluginId;
    private final ToolRegistry toolRegistry;
    private final PluginEventBus eventBus;
    private final InProcessPluginHookRegistry hookRegistry;
    private final PluginConfigStore configStore;
    private final ScopedSystemPromptAssembler promptAssembler;
    private final List<AutoCloseable> registrations = new ArrayList<>();

    /**
     * 创建插件上下文。
     * <p>五个宿主服务都不允许为空；上下文创建后才会交给插件 configure。
     */
    SpringPluginContext(String pluginId, ToolRegistry toolRegistry, PluginEventBus eventBus,
                        InProcessPluginHookRegistry hookRegistry, PluginConfigStore configStore,
                        ScopedSystemPromptAssembler promptAssembler) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.hookRegistry = Objects.requireNonNull(hookRegistry, "hookRegistry");
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.promptAssembler = Objects.requireNonNull(promptAssembler, "promptAssembler");
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    /**
     * 把插件工具适配为宿主可调用定义并注册。
     * <p>执行流程：包装 InProcessToolBridge -> PluginToolDefinition 生成限定名 ->
     * 注册 ToolRegistry -> 把注销器加入 registrations。
     * <p>数据示例：pluginId="time"、tool.name="now"，注册后的限定名为 plugin__time__now。
     */
    @Override
    public AutoCloseable registerTool(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool");
        PluginToolDefinition adapted = new PluginToolDefinition(
                pluginId,
                new InProcessToolBridge(tool),
                new IPluginToolBridge.PluginToolDescriptor(
                        tool.name(), tool.description(), tool.parameters())
        );
        Runnable disposer = toolRegistry.register(adapted);
        String qualifiedName = adapted.name();
        AutoCloseable registration = new PluginToolRegistration(qualifiedName, disposer::run);
        registrations.add(registration);
        return registration;
    }

    /**
     * 登记插件提供的自定义资源释放器。
     * <p>该释放器会在插件停止、热重载或启动失败回滚时执行。
     */
    @Override
    public void registerDisposer(AutoCloseable disposer) {
        Objects.requireNonNull(disposer, "disposer");
        registrations.add(disposer);
    }

    /**
     * 订阅插件事件，并把取消订阅器交给宿主生命周期管理。
     */
    @Override
    public AutoCloseable subscribe(String eventName, PluginEventHandler handler) {
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(handler, "handler");
        AutoCloseable disposer = eventBus.subscribe(eventName, pluginId, handler);
        registrations.add(disposer);
        return disposer;
    }

    /**
     * 以当前插件为来源发布事件。
     * <p>timestamp 由事件总线内部的 PluginEvent 自动生成。
     */
    @Override
    public void emit(String eventName, Object payload) {
        Objects.requireNonNull(eventName, "eventName");
        eventBus.publish(new PluginEvent(pluginId, eventName, payload, null));
    }

    /**
     * 注册进程内插件 Hook。
     * <p>hookPoint 必须是宿主 HookPoint 的枚举名，例如 PRE_TOOL_USE。
     */
    @Override
    public AutoCloseable registerHook(String hookPoint, PluginHook hook) {
        Objects.requireNonNull(hookPoint, "hookPoint");
        Objects.requireNonNull(hook, "hook");
        AutoCloseable disposer = hookRegistry.register(hookPoint, pluginId, hook);
        registrations.add(disposer);
        return disposer;
    }

    /**
     * 按当前插件 ID 从配置存储读取配置。
     */
    @Override
    public java.util.Optional<String> getConfig(String key) {
        return configStore.get(pluginId, key);
    }

    /**
     * 注册插件贡献的系统提示词段落。
     * <p>执行流程：生成 plugin__<pluginId>__<name> -> 注册 PromptSection -> 登记移除器。
     * <p>数据示例：pluginId="time"、name="rules"，实际段落名为 plugin__time__rules。
     */
    @Override
    public AutoCloseable registerSystemPrompt(String name, int order, String text) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
        String qualifiedName = "plugin__" + pluginId + "__" + name;
        ScopedSystemPromptAssembler.PromptSection section =
                new ScopedSystemPromptAssembler.PromptSection(qualifiedName, order, text);
        promptAssembler.registerSection(section);
        AutoCloseable disposer = () -> promptAssembler.removeSection(qualifiedName);
        registrations.add(disposer);
        return disposer;
    }

    /**
     * 返回上下文中当前已注册的工具限定名，主要用于启动和测试诊断。
     */
    List<String> registeredToolNames() {
        return registrations.stream()
                .filter(PluginToolRegistration.class::isInstance)
                .map(PluginToolRegistration.class::cast)
                .map(PluginToolRegistration::qualifiedName)
                .toList();
    }

    /**
     * 逆序关闭所有插件贡献项。
     * <p>执行流程：复制 registrations 并清空 -> 从最后一个注册项向前 close -> 记录并抛出第一个异常。
     * 即使某个资源关闭失败，也会继续释放其余资源。
     */
    @Override
    public synchronized void close() throws Exception {
        List<AutoCloseable> ordered = new ArrayList<>(registrations);
        registrations.clear();
        Exception firstFailure = null;
        for (int index = ordered.size() - 1; index >= 0; index--) {
            try {
                ordered.get(index).close();
            } catch (Exception e) {
                if (firstFailure == null) firstFailure = e;
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * 一个已注册插件工具的限定名及其注销器。
     */
    record PluginToolRegistration(String qualifiedName, AutoCloseable disposer)
            implements AutoCloseable {

        /**
         * 创建工具注册记录；限定名和注销器都必须存在。
         */
        PluginToolRegistration {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(disposer, "disposer");
        }

        /**
         * 关闭注册记录并调用底层工具注销器。
         */
        @Override
        public void close() throws Exception {
            disposer.close();
        }
    }
}
