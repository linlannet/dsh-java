package net.linlan.dsh.domain.guard.reminder;

import net.linlan.dsh.domain.guard.model.valobj.GuardDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DuplicateToolGuard 重复工具调用守卫")
class DuplicateToolGuardTest {

    @Test
    @DisplayName("首次调用返回 allow 无提醒")
    void firstCallReturnsAllowWithoutReminder() {
        var guard = new DuplicateToolGuard();
        GuardDecision decision = guard.check("fs_read", "{\"path\":\"/tmp\"}");
        assertEquals(GuardDecision.Kind.ALLOW, decision.kind());
        assertTrue(decision.additionalContext().isEmpty());
    }

    @Test
    @DisplayName("连续两次相同调用返回 allow 带提醒")
    void consecutiveIdenticalCallsReturnReminder() {
        var guard = new DuplicateToolGuard();
        guard.check("fs_read", "{\"path\":\"/tmp\"}");
        GuardDecision second = guard.check("fs_read", "{\"path\":\"/tmp\"}");
        assertEquals(GuardDecision.Kind.ALLOW, second.kind());
        assertTrue(second.additionalContext().isPresent());
        assertTrue(second.additionalContext().get().contains("Duplicate tool call detected"));
        assertTrue(second.additionalContext().get().contains("fs_read"));
    }

    @Test
    @DisplayName("相同工具不同参数不触发提醒")
    void sameToolDifferentArgsNoReminder() {
        var guard = new DuplicateToolGuard();
        guard.check("fs_read", "{\"path\":\"/a\"}");
        GuardDecision second = guard.check("fs_read", "{\"path\":\"/b\"}");
        assertEquals(GuardDecision.Kind.ALLOW, second.kind());
        assertTrue(second.additionalContext().isEmpty());
    }

    @Test
    @DisplayName("不同工具相同参数不触发提醒")
    void differentToolSameArgsNoReminder() {
        var guard = new DuplicateToolGuard();
        guard.check("fs_read", "{\"path\":\"/tmp\"}");
        GuardDecision second = guard.check("fs_write", "{\"path\":\"/tmp\"}");
        assertEquals(GuardDecision.Kind.ALLOW, second.kind());
        assertTrue(second.additionalContext().isEmpty());
    }

    @Test
    @DisplayName("三次连续相同调用第三次仍触发提醒")
    void thirdConsecutiveIdenticalStillTriggers() {
        var guard = new DuplicateToolGuard();
        guard.check("shell", "{}");
        guard.check("shell", "{}");
        GuardDecision third = guard.check("shell", "{}");
        assertEquals(GuardDecision.Kind.ALLOW, third.kind());
        assertTrue(third.additionalContext().isPresent());
    }

    @Test
    @DisplayName("AAB 模式：A A B 中 B 不触发")
    void aabPatternBDoesNotTrigger() {
        var guard = new DuplicateToolGuard();
        guard.check("shell", "{}");
        guard.check("shell", "{}");
        GuardDecision b = guard.check("fs_read", "{}");
        assertTrue(b.additionalContext().isEmpty());
    }

    @Test
    @DisplayName("ABA 模式：A B A 中第二个 A 不触发")
    void abaPatternSecondADoesNotTrigger() {
        var guard = new DuplicateToolGuard();
        guard.check("shell", "{}");
        guard.check("fs_read", "{}");
        GuardDecision secondA = guard.check("shell", "{}");
        assertTrue(secondA.additionalContext().isEmpty());
    }

    @Test
    @DisplayName("null 参数安全处理")
    void nullArgumentsHandledSafely() {
        var guard = new DuplicateToolGuard();
        GuardDecision first = guard.check(null, null);
        assertTrue(first.additionalContext().isEmpty());
        GuardDecision second = guard.check(null, null);
        assertTrue(second.additionalContext().isPresent());
    }

    @Test
    @DisplayName("reset 后相同调用不触发提醒")
    void resetClearsLastCall() {
        var guard = new DuplicateToolGuard();
        guard.check("shell", "{}");
        guard.reset();
        GuardDecision after = guard.check("shell", "{}");
        assertTrue(after.additionalContext().isEmpty());
    }

    @Test
    @DisplayName("isLastCall 反映上次调用快照")
    void isLastCallReflectsSnapshot() {
        var guard = new DuplicateToolGuard();
        guard.check("fs_read", "{\"p\":\"x\"}");
        assertTrue(guard.isLastCall("fs_read", "{\"p\":\"x\"}"));
        assertFalse(guard.isLastCall("fs_write", "{\"p\":\"x\"}"));
    }

    @Test
    @DisplayName("空参数与 null 参数等价")
    void emptyAndNullArgumentsEquivalent() {
        var guard = new DuplicateToolGuard();
        guard.check("shell", null);
        GuardDecision second = guard.check("shell", "");
        assertTrue(second.additionalContext().isPresent());
    }
}
