package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionInput;
import net.linlan.dsh.domain.model.valobj.ToolExecutionMode;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ToolRegistry} implementation.
 * <p>
 * Tools register at startup (or dynamically via plugins) and are held in a
 * thread-safe map. This mirrors the TS {@code ToolRuntime} registry portion.
 *
 *
 */
public class InMemoryToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, Runnable> disposers = new ConcurrentHashMap<>();

    @Override
    public Runnable register(ToolDefinition definition) {
        String name = definition.name();
        if (tools.containsKey(name)) {
            throw new IllegalStateException("Tool already registered: " + name);
        }
        tools.put(name, definition);
        Runnable disposer = () -> {
            tools.remove(name);
            disposers.remove(name);
        };
        disposers.put(name, disposer);
        return disposer;
    }
    /**
     * lookup。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param name 名称。
     * @return 方法执行结果。
     */


    @Override
    public Optional<ToolDefinition> lookup(String name) {
        return Optional.ofNullable(tools.get(name));
    }
    /**
     * all。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Collection<ToolDefinition> all() {
        return tools.values();
    }
    /**
     * execution Mode。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param exec exec参数。
     * @return 方法执行结果。
     */


    @Override
    public ToolExecutionMode executionMode(ToolExecutionInput exec) {
        ToolDefinition def = tools.get(exec.name());
        if (def == null) return ToolExecutionMode.EXCLUSIVE;
        return def.isConcurrencySafe(exec.arguments())
                ? ToolExecutionMode.PARALLEL
                : ToolExecutionMode.EXCLUSIVE;
    }
}
