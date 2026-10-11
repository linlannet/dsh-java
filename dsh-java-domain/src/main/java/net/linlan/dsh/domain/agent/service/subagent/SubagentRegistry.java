package net.linlan.dsh.domain.agent.service.subagent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry for subagent providers.
 * <p>
 * Mirrors TS {@code ctx.subagents}: a service that routes subagent
 * start requests to the appropriate provider by name.
 *
 * <p>调用流程：模型输出 → 工具调用 → 结果回填 → 下一步模型调用；每一步写入会话事件。</p>
 * <p>示例：模型请求执行 fs_read 后，工具结果作为观察值进入下一轮模型输入。</p>
 *
 */
public class SubagentRegistry {

    private final Map<String, SubagentProvider> providers = new ConcurrentHashMap<>();
    private final AtomicInteger runIdCounter = new AtomicInteger(0);

    /**
     * 注册目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param provider 模型提供方。
     */
    public void register(SubagentProvider provider) {
        providers.put(provider.name(), provider);
    }

    /**
     * 处理unregister。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name 名称。
     */
    public void unregister(String name) {
        providers.remove(name);
    }

    /**
     * 解析e。
     */
    public SubagentProvider resolve(String name) {
        SubagentProvider p = providers.get(name);
        if (p == null) {
            throw new IllegalArgumentException("no subagent provider registered for: " + name);
        }
        return p;
    }

    /**
     * 处理listProviderNames提供方名称列表。
     * @return 领域处理结果。
     */
    public List<String> listProviderNames() {
        return List.copyOf(providers.keySet());
    }

    /**
     * 返回策略树中的下一个节点。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String nextRunId() {
        return "run-" + runIdCounter.incrementAndGet();
    }
}
