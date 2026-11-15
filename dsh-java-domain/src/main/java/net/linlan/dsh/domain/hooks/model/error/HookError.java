package net.linlan.dsh.domain.hooks.model.error;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * HookError 表示 Hook 拦截或执行失败。
 *
 * <p>调用流程：工具执行管线检查 PreToolUse Hook → 命中阻止规则 → 抛出/记录本错误。</p>
 * <p>示例：被 PreToolUse Hook 阻止时返回 {@code HOOK_BLOCKED}。</p>
 */
public class HookError extends HarnessError {

    /** Hook 阻止工具执行。 */
    public static final String HOOK_BLOCKED = "HOOK_BLOCKED";

    /**
     * 使用错误码与消息创建。
     *
     * @param errorCode 错误码
     * @param message   可读消息
     */
    public HookError(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建 PreToolUse Hook 拦截错误。
     *
     * @return Hook 错误
     */
    public static HookError blocked() {
        return new HookError(HOOK_BLOCKED, "Blocked by PreToolUse hook");
    }
}
