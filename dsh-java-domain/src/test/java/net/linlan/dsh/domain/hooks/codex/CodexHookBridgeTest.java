package net.linlan.dsh.domain.hooks.codex;

import net.linlan.dsh.domain.hooks.model.valobj.HookOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CodexHookBridge Codex hook 协议桥接")
class CodexHookBridgeTest {

    @Test
    @DisplayName("null 输入返回空 HookOutput")
    void nullInputReturnsEmptyOutput() {
        var bridge = new CodexHookBridge();
        HookOutput output = bridge.bridge(null);
        assertNotNull(output);
        assertEquals(0, output.exitCode());
        assertEquals("", output.stderr());
        assertEquals("", output.stdout());
        assertTrue(output.continueFlag().isEmpty());
        assertTrue(output.decision().isEmpty());
    }

    @Test
    @DisplayName("空字符串输入返回空 HookOutput")
    void blankInputReturnsEmptyOutput() {
        var bridge = new CodexHookBridge();
        HookOutput output = bridge.bridge("   ");
        assertNotNull(output);
        assertEquals(0, output.exitCode());
    }

    @Test
    @DisplayName("解析 block decision 和 reason")
    void parsesBlockDecisionAndReason() {
        var bridge = new CodexHookBridge();
        String input = "{\"decision\":\"block\",\"reason\":\"dangerous command\"}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.decision().isPresent());
        assertEquals(HookOutput.HookDecision.BLOCK, output.decision().get());
        assertTrue(output.reason().isPresent());
        assertEquals("dangerous command", output.reason().get());
    }

    @Test
    @DisplayName("解析 continue=false 和 stopReason")
    void parsesContinueFalseAndStopReason() {
        var bridge = new CodexHookBridge();
        String input = "{\"continue\":false,\"stopReason\":\"blocked\"}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.continueFlag().isPresent());
        assertFalse(output.continueFlag().get());
        assertTrue(output.stopReason().isPresent());
        assertEquals("blocked", output.stopReason().get());
    }

    @Test
    @DisplayName("解析 exit_code 和 stdout/stderr")
    void parsesExitCodeAndStreams() {
        var bridge = new CodexHookBridge();
        String input = "{\"exit_code\":2,\"stdout\":\"ok\",\"stderr\":\"warn\"}";
        HookOutput output = bridge.bridge(input);
        assertEquals(2, output.exitCode());
        assertEquals("ok", output.stdout());
        assertEquals("warn", output.stderr());
    }

    @Test
    @DisplayName("解析 additionalContext 和 systemMessage")
    void parsesAdditionalContextAndSystemMessage() {
        var bridge = new CodexHookBridge();
        String input = "{\"additionalContext\":\"ctx\",\"systemMessage\":\"sys\"}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.additionalContext().isPresent());
        assertEquals("ctx", output.additionalContext().get());
        assertTrue(output.systemMessage().isPresent());
        assertEquals("sys", output.systemMessage().get());
    }

    @Test
    @DisplayName("解析 allow decision")
    void parsesAllowDecision() {
        var bridge = new CodexHookBridge();
        String input = "{\"decision\":\"allow\"}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.decision().isPresent());
        assertEquals(HookOutput.HookDecision.ALLOW, output.decision().get());
    }

    @Test
    @DisplayName("continue=true 解析为 true")
    void parsesContinueTrue() {
        var bridge = new CodexHookBridge();
        String input = "{\"continue\":true}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.continueFlag().isPresent());
        assertTrue(output.continueFlag().get());
    }

    @Test
    @DisplayName("未知 decision 值返回 empty")
    void unknownDecisionReturnsEmpty() {
        var bridge = new CodexHookBridge();
        String input = "{\"decision\":\"unknown\"}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.decision().isEmpty());
    }

    @Test
    @DisplayName("toCodexJson 序列化 HookOutput 为 JSON")
    void toCodexJsonSerializesOutput() {
        var bridge = new CodexHookBridge();
        HookOutput output = new HookOutput(
                0, "", "hello", Optional.of(true), Optional.empty(),
                Optional.of(HookOutput.HookDecision.ALLOW), Optional.empty(), Optional.empty(), Optional.empty()
        );
        String json = bridge.toCodexJson(output);
        assertNotNull(json);
        assertTrue(json.contains("\"stdout\":\"hello\""));
        assertTrue(json.contains("\"continue\":true"));
        assertTrue(json.contains("\"decision\":\"ALLOW\""));
    }

    @Test
    @DisplayName("toCodexJson 处理 null 字段")
    void toCodexJsonHandlesNullFields() {
        var bridge = new CodexHookBridge();
        HookOutput output = new HookOutput(
                null, null, null, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        String json = bridge.toCodexJson(output);
        assertNotNull(json);
        assertTrue(json.contains("\"exit_code\":null"));
        assertTrue(json.contains("\"stderr\":null"));
    }

    @Test
    @DisplayName("bridge 解析含转义字符的字符串")
    void parsesEscapedString() {
        var bridge = new CodexHookBridge();
        String input = "{\"reason\":\"line1\\nline2\"}";
        HookOutput output = bridge.bridge(input);
        assertTrue(output.reason().isPresent());
        assertEquals("line1\nline2", output.reason().get());
    }

    @Test
    @DisplayName("完整 Codex hook 输入解析")
    void fullCodexHookInputParsed() {
        var bridge = new CodexHookBridge();
        String input = "{\"exit_code\":0,\"stdout\":\"\",\"stderr\":\"\",\"continue\":true,\"decision\":\"approve\",\"reason\":\"ok\",\"additionalContext\":\"ctx\",\"systemMessage\":\"msg\",\"stopReason\":null}";
        HookOutput output = bridge.bridge(input);
        assertEquals(0, output.exitCode());
        assertTrue(output.continueFlag().isPresent());
        assertTrue(output.continueFlag().get());
        assertEquals(HookOutput.HookDecision.APPROVE, output.decision().get());
        assertEquals("ok", output.reason().get());
        assertEquals("ctx", output.additionalContext().get());
        assertEquals("msg", output.systemMessage().get());
    }
}
