package net.linlan.dsh.domain.hooks.dispatcher;

import net.linlan.dsh.domain.hooks.claudecode.ClaudeCodeHookBridge;
import net.linlan.dsh.domain.hooks.model.valobj.HookBehavior;
import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HookPointDispatcher Hook 调度")
class HookPointDispatcherTest {
    @Test
    @DisplayName("dispatch 返回 ALLOW 行为")
    void dispatchAllow() {
        var dispatcher = new HookPointDispatcher(new ClaudeCodeHookBridge());
        var behavior = dispatcher.dispatch(HookPoint.PRE_TOOL_USE, Map.of());
        assertEquals(HookBehavior.ALLOW, behavior);
    }

    @Test
    @DisplayName("dispatch 异常时降级为 ALLOW")
    void dispatchExceptionDegrade() {
        var dispatcher = new HookPointDispatcher(new ClaudeCodeHookBridge());
        var behavior = dispatcher.dispatch(HookPoint.SESSION_START, Map.of());
        assertEquals(HookBehavior.ALLOW, behavior);
    }
}
