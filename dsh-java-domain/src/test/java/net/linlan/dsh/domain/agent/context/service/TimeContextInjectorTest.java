package net.linlan.dsh.domain.agent.context.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeContextInjector 时间上下文注入")
class TimeContextInjectorTest {

    @Test
    @DisplayName("inject 返回包含当前时间戳的文本")
    void injectContainsTimestamp() {
        var injector = new TimeContextInjector(() -> Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        String result = injector.inject();
        assertNotNull(result);
        assertTrue(result.contains("Current time:"));
        assertTrue(result.contains("Elapsed"));
    }

    @Test
    @DisplayName("inject 计算 elapsed 时间正确")
    void injectCalculatesElapsed() {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var now = Instant.parse("2026-01-01T00:00:05Z");
        var injector = new TimeContextInjector(() -> now, start);
        String result = injector.inject();
        assertTrue(result.contains("5000ms"));
    }

    @Test
    @DisplayName("contextName 返回 time")
    void contextNameIsTime() {
        var injector = new TimeContextInjector();
        assertEquals("time", injector.contextName());
    }

    @Test
    @DisplayName("null clock 抛异常")
    void nullClockRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TimeContextInjector(null, Instant.now()));
    }

    @Test
    @DisplayName("null sessionStart 使用当前时间")
    void nullSessionStartUsesNow() {
        var injector = new TimeContextInjector(() -> Instant.now(), null);
        String result = injector.inject();
        assertNotNull(result);
        assertTrue(result.contains("Elapsed"));
    }

    @Test
    @DisplayName("register null assembler 不抛异常")
    void registerNullAssemblerSafe() {
        var injector = new TimeContextInjector();
        assertDoesNotThrow(() -> injector.register(null));
    }
}
