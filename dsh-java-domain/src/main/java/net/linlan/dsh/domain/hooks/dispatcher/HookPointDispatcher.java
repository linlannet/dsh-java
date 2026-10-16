package net.linlan.dsh.domain.hooks.dispatcher;

import net.linlan.dsh.domain.hooks.claudecode.ClaudeCodeHookBridge;
import net.linlan.dsh.domain.hooks.model.valobj.HookBehavior;
import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import java.util.Map;
import java.util.Objects;

/**
 * HookPointDispatcher 复用 CodexHookBridge + ClaudeCodeHookBridge，调度对应 command 钩子。
 * 实验性扩展，默认关闭。
 */
public class HookPointDispatcher {

    private final ClaudeCodeHookBridge claudeBridge;

    public HookPointDispatcher(ClaudeCodeHookBridge claudeBridge) {
        this.claudeBridge = Objects.requireNonNull(claudeBridge);
    }

    public HookBehavior dispatch(HookPoint point, Map<String, Object> context) {
        Objects.requireNonNull(point, "point 不能为空");
        try {
            var result = claudeBridge.executeHook(point, context);
            if (!result.success()) {
                return HookBehavior.ALLOW;
            }
            return HookBehavior.ALLOW;
        } catch (Exception e) {
            return HookBehavior.ALLOW;
        }
    }
}
