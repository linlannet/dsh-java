package net.linlan.dsh.domain.hooks.claudecode;

import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import java.util.Map;
import java.util.Objects;

/**
 * ClaudeCodeHookBridge 执行现有 Claude Code hooks.json 中的 command 钩子。
 * 实验性扩展，默认关闭。
 */
public class ClaudeCodeHookBridge {

    public HookResult executeHook(HookPoint point, Map<String, Object> context) {
        Objects.requireNonNull(point, "point 不能为空");
        return new HookResult(true, "", "");
    }

    public record HookResult(boolean success, String stdout, String stderr) { }
}
