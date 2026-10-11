package net.linlan.dsh.domain.hooks.service;

import net.linlan.dsh.domain.hooks.model.valobj.HookOutput;
import net.linlan.dsh.domain.hooks.model.valobj.MergedHookOutcome;
import java.util.List;
import java.util.Optional;

/**
 * Restrictive merge of hook outputs: block/deny wins over allow/approve;
 * additional contexts and system messages are concatenated; stop wins.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public final class HookOutputMerger {

    /**
     * 处理Hook Output Merger。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private HookOutputMerger() {}

    /**
     * 处理merge。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param outputs outputs参数。
     * @return 方法执行结果。
     */
    public static MergedHookOutcome merge(List<HookOutput> outputs) {
        Optional<HookOutput.HookDecision> decision = Optional.empty();
        StringBuilder additionalContext = new StringBuilder();
        StringBuilder systemMessage = new StringBuilder();
        boolean stop = false;
        Optional<String> stopReason = Optional.empty();

        for (HookOutput output : outputs) {
            // Decision: block/deny wins
            if (output.decision().isPresent()) {
                HookOutput.HookDecision d = output.decision().get();
                if (d == HookOutput.HookDecision.BLOCK || d == HookOutput.HookDecision.DENY) {
                    decision = Optional.of(d);
                } else if (decision.isEmpty()) {
                    decision = Optional.of(d);
                }
            }
            // Continue flag: false means stop
            if (output.continueFlag().isPresent() && !output.continueFlag().get()) {
                stop = true;
                if (stopReason.isEmpty()) stopReason = output.stopReason();
            }
            output.additionalContext().ifPresent(s -> {
                if (!additionalContext.isEmpty()) additionalContext.append("\n");
                additionalContext.append(s);
            });
            output.systemMessage().ifPresent(s -> {
                if (!systemMessage.isEmpty()) systemMessage.append("\n");
                systemMessage.append(s);
            });
        }

        return new MergedHookOutcome(
                decision,
                additionalContext.toString(),
                systemMessage.toString(),
                stop,
                stopReason
        );
    }
}
