package net.linlan.dsh.domain.hooks.model.valobj;

import java.util.Optional;

/**
 * HookOutput 表示HookOutput。
 */
public record HookOutput(
        Integer exitCode,
        String stderr,
        String stdout,
        Optional<Boolean> continueFlag,
        Optional<String> stopReason,
        Optional<HookDecision> decision,
        Optional<String> reason,
        Optional<String> additionalContext,
        Optional<String> systemMessage
) {
    /**
     * HookDecision 定义当前模块中的enum契约与职责。
     */
    public enum HookDecision { APPROVE, ALLOW, BLOCK, DENY, ASK }
}
