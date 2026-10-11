package net.linlan.dsh.domain.hooks.model.valobj;

import java.util.Optional;

/**
 * MergedHookOutcome 表示MergedHookOutcome。
 */
public record MergedHookOutcome(
        Optional<HookOutput.HookDecision> decision,
        String additionalContext,
        String systemMessage,
        boolean stop,
        Optional<String> stopReason
) {}
