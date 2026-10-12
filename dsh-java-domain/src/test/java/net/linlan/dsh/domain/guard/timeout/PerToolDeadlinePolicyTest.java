package net.linlan.dsh.domain.guard.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PerToolDeadlinePolicy per-tool 超时策略")
class PerToolDeadlinePolicyTest {

    @Test
    @DisplayName("配置的工具名返回对应 Duration")
    void configuredToolReturnsConfiguredDuration() {
        var policy = new PerToolDeadlinePolicy(
                Map.of("fs_read", Duration.ofSeconds(10), "fs_grep", Duration.ofSeconds(20)),
                Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(10), policy.deadline("fs_read"));
        assertEquals(Duration.ofSeconds(20), policy.deadline("fs_grep"));
    }

    @Test
    @DisplayName("未配置的工具名返回默认 Duration")
    void unconfiguredToolReturnsDefaultDuration() {
        var policy = new PerToolDeadlinePolicy(
                Map.of("fs_read", Duration.ofSeconds(10)),
                Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30), policy.deadline("fs_write"));
    }

    @Test
    @DisplayName("默认构造使用 30 秒默认值")
    void defaultConstructorUses30Seconds() {
        var policy = new PerToolDeadlinePolicy(Map.of("fs_read", Duration.ofSeconds(10)));
        assertEquals(Duration.ofSeconds(30), policy.defaultDeadline());
        assertEquals(Duration.ofSeconds(30), policy.deadline("unknown_tool"));
    }

    @Test
    @DisplayName("deadlineOrDefault 使用传入默认值而非策略默认值")
    void deadlineOrDefaultUsesProvidedDefault() {
        var policy = new PerToolDeadlinePolicy(
                Map.of("fs_read", Duration.ofSeconds(10)),
                Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(10), policy.deadlineOrDefault("fs_read", Duration.ofSeconds(60)));
        assertEquals(Duration.ofSeconds(60), policy.deadlineOrDefault("unknown", Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("hasToolDeadline 正确反映配置状态")
    void hasToolDeadlineReflectsConfiguration() {
        var policy = new PerToolDeadlinePolicy(
                Map.of("fs_read", Duration.ofSeconds(10)),
                Duration.ofSeconds(30));
        assertTrue(policy.hasToolDeadline("fs_read"));
        assertFalse(policy.hasToolDeadline("fs_write"));
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgumentsRejected() {
        var policy = new PerToolDeadlinePolicy(Map.of(), Duration.ofSeconds(30));
        assertThrows(NullPointerException.class, () -> policy.deadline(null));
        assertThrows(NullPointerException.class, () -> policy.deadlineOrDefault("x", null));
        assertThrows(NullPointerException.class, () -> new PerToolDeadlinePolicy(null));
        assertThrows(NullPointerException.class, () -> new PerToolDeadlinePolicy(Map.of(), null));
    }

    @Test
    @DisplayName("空配置表时所有工具返回默认值")
    void emptyConfigReturnsDefaultForAll() {
        var policy = new PerToolDeadlinePolicy(Map.of(), Duration.ofSeconds(15));
        assertEquals(Duration.ofSeconds(15), policy.deadline("any_tool"));
        assertEquals(Duration.ofSeconds(15), policy.deadline("another_tool"));
    }
}
