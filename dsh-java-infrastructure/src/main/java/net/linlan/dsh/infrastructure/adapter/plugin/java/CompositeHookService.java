package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.hooks.IHookService;
import net.linlan.dsh.domain.hooks.model.valobj.HookDialect;
import net.linlan.dsh.domain.hooks.model.valobj.HookOutput;
import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import net.linlan.dsh.domain.hooks.model.valobj.MergedHookOutcome;
import net.linlan.dsh.domain.hooks.service.HookOutputMerger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 聚合插件 Hook 与 Shell Hook 的统一执行服务。
 * <p>核心流程：先执行进程内插件 Hook，再执行原有 Shell Hook，最后用限制性策略合并结果。
 * 只要任一来源要求 BLOCK/DENY/STOP，合并后的执行链就会被阻断。
 * <p>数据示例：PRE_TOOL_USE 中插件 Hook 返回 DENY，即使 Shell Hook 放行，最终也不会执行工具。
 * <p>该服务标注 @Primary，AgentRunFactory 到 ToolCallExecutor 的注入点会优先使用它。
 */
@Service
@Primary
public final class CompositeHookService implements IHookService {

    private final IHookService delegate;
    private final InProcessPluginHookRegistry pluginHookRegistry;

    /**
     * 注入原有 Hook 服务和插件 Hook 注册表。
     */
    public CompositeHookService(IHookService delegate, InProcessPluginHookRegistry pluginHookRegistry) {
        this.delegate = delegate;
        this.pluginHookRegistry = pluginHookRegistry;
    }

    /**
     * 执行插件 Hook 和原有 Shell Hook，并合并两者的结果。
     * <p>执行流程：收集插件 Hook 输出 -> 执行 delegate Shell Hook -> 有实际输出时转换为 HookOutput ->
     * 使用 HookOutputMerger 限制性合并；没有任何输出时返回空结果。
     */
    @Override
    public MergedHookOutcome runHooks(HookDialect dialect, HookPoint point, String toolName,
                                      String payloadJson, Map<String, String> environment) {
        List<HookOutput> outputs = new ArrayList<>();

        outputs.addAll(pluginHookRegistry.runHooks(point, toolName, payloadJson));

        MergedHookOutcome shellOutcome = delegate.runHooks(dialect, point, toolName, payloadJson, environment);
        if (!shellOutcome.decision().isEmpty()
                || !shellOutcome.additionalContext().isEmpty()
                || !shellOutcome.systemMessage().isEmpty()
                || shellOutcome.stop()) {
            outputs.add(toHookOutput(shellOutcome));
        }

        if (outputs.isEmpty()) {
            return new MergedHookOutcome(Optional.empty(), "", "", false, Optional.empty());
        }
        return HookOutputMerger.merge(outputs);
    }

    /**
     * 把原有 Hook 合并结果转换为统一 HookOutput。
     * <p>stop=true 映射为 allowed=false，便于合并器识别阻断语义。
     */
    private static HookOutput toHookOutput(MergedHookOutcome outcome) {
        return new HookOutput(
                0, null, null,
                Optional.of(!outcome.stop()),
                outcome.stopReason(),
                outcome.decision(),
                outcome.stopReason(),
                outcome.additionalContext().isEmpty() ? Optional.empty() : Optional.of(outcome.additionalContext()),
                outcome.systemMessage().isEmpty() ? Optional.empty() : Optional.of(outcome.systemMessage())
        );
    }
}
