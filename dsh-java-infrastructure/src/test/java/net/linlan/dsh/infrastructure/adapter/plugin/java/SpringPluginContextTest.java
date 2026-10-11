package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.spi.JavaHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginEvent;
import net.linlan.dsh.domain.spi.PluginContext;
import net.linlan.dsh.domain.spi.PluginHook;
import net.linlan.dsh.domain.spi.PluginHookResult;
import net.linlan.dsh.domain.spi.PluginConfigStore;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * SpringPluginContext 生命周期测试。
 * <p>覆盖插件工具限定名、贡献项逆序回收、事件订阅隔离和插件 Hook 的注册/执行语义。
 * <p>调用流程：插件清单 → 运行时加载 → 工具/Hook 注册 → 调用与卸载。</p>
 * <p>示例：插件目录中的 manifest 决定运行时类型与入口。</p>
 */
class SpringPluginContextTest {

    /**
     * 旧式 tools() 插件注册后，工具名必须自动加上插件前缀。
     */
    @Test
    void registersLegacyToolsWithQualifiedNames() {
        RecordingToolRegistry registry = new RecordingToolRegistry();
        SpringPluginContext context = createContext(registry);
        JavaHarnessPlugin plugin = new JavaHarnessPlugin() {
            @Override public String pluginId() { return "demo"; }
            @Override public List<ToolDefinition> tools() { return List.of(tool("echo")); }
        };

        plugin.configure(context);

        assertEquals(List.of("plugin__demo__echo"), context.registeredToolNames());
        assertEquals(List.of("plugin__demo__echo"), registry.registeredNames);
    }

    /**
     * 关闭上下文时必须先释放自定义资源，再按注册相反顺序注销工具。
     */
    @Test
    void closesRegistrationsInReverseOrder() throws Exception {
        RecordingToolRegistry registry = new RecordingToolRegistry();
        SpringPluginContext context = createContext(registry);
        List<String> disposed = new java.util.ArrayList<>();
        context.registerTool(tool("one"));
        context.registerTool(tool("two"));
        context.registerDisposer(() -> disposed.add("resource"));

        context.close();

        assertEquals(List.of("resource"), disposed);
        assertEquals(List.of("plugin__demo__two", "plugin__demo__one"), registry.unregisteredNames);
        assertTrue(context.registeredToolNames().isEmpty());
    }

    /**
     * 事件订阅能收到插件事件，上下文关闭后订阅必须被清理。
     */
    @Test
    void eventBusPublishesAndDisposesSubscribers() throws Exception {
        InProcessPluginEventBus bus = new InProcessPluginEventBus();
        SpringPluginContext context = new SpringPluginContext("demo", new RecordingToolRegistry(), bus,
                new InProcessPluginHookRegistry(), new InMemoryPluginConfigStore(),
                new ScopedSystemPromptAssembler());
        List<String> received = new java.util.ArrayList<>();
        context.subscribe("greeting", event -> received.add(event.source() + ":" + event.name()));

        assertEquals(1, bus.subscriberCount("greeting"));
        context.emit("greeting", "hello");
        assertEquals(List.of("demo:greeting"), received);

        context.close();
        assertEquals(0, bus.subscriberCount("greeting"));
    }

    /**
     * 一个事件处理器抛异常时，总线必须继续通知后续处理器。
     */
    @Test
    void eventBusContainsHandlerExceptions() {
        InProcessPluginEventBus bus = new InProcessPluginEventBus();
        List<String> received = new java.util.ArrayList<>();
        bus.subscribe("boom", "a", event -> { throw new IllegalStateException("handler error"); });
        bus.subscribe("boom", "b", event -> received.add(event.name()));

        bus.publish(new PluginEvent("host", "boom", null, null));

        assertEquals(List.of("boom"), received);
    }

    /**
     * 插件 Hook 随上下文注册，也必须随上下文关闭自动注销。
     */
    @Test
    void hookRegistersAndUnregistersWithLifecycle() throws Exception {
        InProcessPluginHookRegistry registry = new InProcessPluginHookRegistry();
        SpringPluginContext context = new SpringPluginContext("demo", new RecordingToolRegistry(),
                new InProcessPluginEventBus(), registry, new InMemoryPluginConfigStore(),
                new ScopedSystemPromptAssembler());

        context.registerHook("PRE_TOOL_USE", (toolName, payload) -> PluginHookResult.block("test block"));
        assertEquals(1, registry.hookCount("PRE_TOOL_USE"));

        context.close();
        assertEquals(0, registry.hookCount("PRE_TOOL_USE"));
    }

    /**
     * PRE_TOOL_USE Hook 返回 block 时，应转换为领域 HookOutput 的 BLOCK 决策和原因。
     */
    @Test
    void hookExecutesAndReturnsBlockDecision() {
        InProcessPluginHookRegistry registry = new InProcessPluginHookRegistry();
        registry.register("PRE_TOOL_USE", "test", (toolName, payload) -> PluginHookResult.block("no"));

        var outputs = registry.runHooks(
                net.linlan.dsh.domain.hooks.model.valobj.HookPoint.PRE_TOOL_USE,
                "shell_execute", "{}");

        assertEquals(1, outputs.size());
        assertEquals(net.linlan.dsh.domain.hooks.model.valobj.HookOutput.HookDecision.BLOCK,
                outputs.get(0).decision().orElse(null));
        assertEquals("no", outputs.get(0).reason().orElse(null));
    }

    /**
     * 创建带完整内存实现的测试上下文。
     */
    private SpringPluginContext createContext(RecordingToolRegistry registry) {
        return new SpringPluginContext("demo", registry,
                new InProcessPluginEventBus(), new InProcessPluginHookRegistry(),
                new InMemoryPluginConfigStore(), new ScopedSystemPromptAssembler());
    }

    /**
     * 创建一个只返回固定文本的测试工具。
     */
    private ToolDefinition tool(String name) {
        return new ToolDefinition() {
            @Override public String name() { return name; }
            @Override public String description() { return "test tool"; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
                return CompletableFuture.completedFuture(
                        (ToolExecutionResult) ToolExecutionResult.ok(List.of(new TextBlock("ok"))));
            }
        };
    }

    /**
     * 记录注册和注销顺序的内存工具注册表。
     */
    private static final class RecordingToolRegistry implements ToolRegistry {
        private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
        private final List<String> registeredNames = new java.util.ArrayList<>();
        private final List<String> unregisteredNames = new java.util.ArrayList<>();

        @Override public Runnable register(ToolDefinition definition) {
            registeredNames.add(definition.name());
            tools.put(definition.name(), definition);
            return () -> {
                tools.remove(definition.name());
                unregisteredNames.add(definition.name());
            };
        }

        @Override public Optional<ToolDefinition> lookup(String name) {
            return Optional.ofNullable(tools.get(name));
        }

        @Override public java.util.Collection<ToolDefinition> all() {
            return List.copyOf(tools.values());
        }

        @Override public net.linlan.dsh.domain.model.valobj.ToolExecutionMode executionMode(
                net.linlan.dsh.domain.model.entity.ToolExecutionInput input) {
            return net.linlan.dsh.domain.model.valobj.ToolExecutionMode.EXCLUSIVE;
        }
    }
}
