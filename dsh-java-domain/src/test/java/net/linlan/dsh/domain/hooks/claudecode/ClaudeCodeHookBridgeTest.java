package net.linlan.dsh.domain.hooks.claudecode;

import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeCodeHookBridge Claude Code Hook")
class ClaudeCodeHookBridgeTest {
    @Test
    @DisplayName("executeHook 返回成功结果")
    void executeHook() {
        var bridge = new ClaudeCodeHookBridge();
        var result = bridge.executeHook(HookPoint.USER_PROMPT_SUBMIT, Map.of());
        assertTrue(result.success());
    }
}
