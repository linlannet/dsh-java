package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.hooks.model.valobj.HookOutput;
import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import net.linlan.dsh.domain.spi.PluginHook;
import net.linlan.dsh.domain.spi.PluginHookResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 进程内插件 Hook 注册表。
 * <p>按 HookPoint 枚举名保存回调，提供注册、执行、批量注销和诊断计数。
 * PRE_TOOL_USE 的 BLOCK/DENY 会由统一 Hook 合并器用于阻断工具；
 * POST_TOOL_USE 结果仅用于观察和附加上下文。
 * <p>数据示例：PRE_TOOL_USE 注册 shell_guard Hook，返回 block("危险命令") 后
 * CompositeHookService 会把该结果合并为阻断。
 */
@Component
public final class InProcessPluginHookRegistry {

    private static final Logger log = LoggerFactory.getLogger(InProcessPluginHookRegistry.class);

    private final Map<String, List<PluginHookEntry>> hooks = new ConcurrentHashMap<>();

    /**
     * 在指定 Hook 点注册一个插件回调。
     * <p>返回的关闭器只移除当前 entry；列表为空时同步移除 hookPoint，避免空列表残留。
     */
    public AutoCloseable register(String hookPoint, String pluginId, PluginHook hook) {
        Objects.requireNonNull(hookPoint, "hookPoint");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(hook, "hook");
        PluginHookEntry entry = new PluginHookEntry(pluginId, hook);
        hooks.computeIfAbsent(hookPoint, key -> new CopyOnWriteArrayList<>()).add(entry);
        return () -> {
            List<PluginHookEntry> list = hooks.get(hookPoint);
            if (list != null) {
                list.remove(entry);
                if (list.isEmpty()) {
                    hooks.remove(hookPoint, list);
                }
            }
        };
    }

    /**
     * 执行指定 Hook 点的全部插件回调，并转换为宿主 HookOutput。
     * <p>执行流程：按 HookPoint 查列表 -> 复制列表避免并发修改 -> 逐个执行 ->
     * 非 null 结果转 HookOutput -> 单个 Hook 异常只记录告警并继续。
     */
    public List<HookOutput> runHooks(HookPoint point, String toolName, String payloadJson) {
        String key = point.name();
        List<PluginHookEntry> entries = hooks.get(key);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<HookOutput> outputs = new ArrayList<>();
        for (PluginHookEntry entry : List.copyOf(entries)) {
            try {
                PluginHookResult result = entry.hook().execute(toolName, payloadJson);
                if (result != null) {
                    outputs.add(toHookOutput(result));
                }
            } catch (Exception e) {
                log.warn("Plugin hook '{}' at '{}' threw exception: {}",
                        entry.pluginId(), key, e.getMessage(), e);
            }
        }
        return outputs;
    }

    /**
     * 移除指定插件的全部 Hook，用于插件停止或卸载。
     */
    public void unregisterAll(String pluginId) {
        hooks.values().forEach(list ->
                list.removeIf(entry -> entry.pluginId().equals(pluginId)));
    }

    /**
     * 返回指定 Hook 点的插件回调数量，用于测试和诊断。
     */
    public int hookCount(String hookPoint) {
        List<PluginHookEntry> list = hooks.get(hookPoint);
        return list == null ? 0 : list.size();
    }

    /**
     * 把插件 Hook 结果转换为领域 HookOutput，供统一合并器处理。
     * <p>BLOCK/DENY 映射为 allowed=false；未知 decision 会被过滤，不阻断执行。
     */
    private static HookOutput toHookOutput(PluginHookResult result) {
        Optional<HookOutput.HookDecision> decision = result.decision()
                .map(d -> {
                    try {
                        return HookOutput.HookDecision.valueOf(d);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull);

        boolean isBlocking = decision.isPresent()
                && (decision.get() == HookOutput.HookDecision.BLOCK
                    || decision.get() == HookOutput.HookDecision.DENY);

        return new HookOutput(
                0,
                null,
                null,
                Optional.of(!isBlocking),
                isBlocking ? result.reason() : Optional.empty(),
                decision,
                result.reason(),
                result.additionalContext(),
                result.systemMessage()
        );
    }

    /**
     * 一个插件 Hook 注册项：来源插件和回调。
     */
    private record PluginHookEntry(String pluginId, PluginHook hook) {}
}
