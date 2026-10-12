package net.linlan.dsh.domain.agent.context.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TmuxContextInjector tmux 上下文注入")
class TmuxContextInjectorTest {

    @Test
    @DisplayName("inject 返回 tmux 信息")
    void injectReturnsTmuxInfo() {
        var injector = new TmuxContextInjector(() -> "session=0:window=1:pane=2");
        String result = injector.inject();
        assertTrue(result.startsWith("tmux: "));
        assertTrue(result.contains("session=0"));
    }

    @Test
    @DisplayName("空 tmux 信息返回 not available")
    void emptyTmuxInfoReturnsNotAvailable() {
        var injector = new TmuxContextInjector(() -> "");
        String result = injector.inject();
        assertEquals("tmux: not available", result);
    }

    @Test
    @DisplayName("null tmux 信息返回 not available")
    void nullTmuxInfoReturnsNotAvailable() {
        var injector = new TmuxContextInjector(() -> null);
        String result = injector.inject();
        assertEquals("tmux: not available", result);
    }

    @Test
    @DisplayName("contextName 返回 tmux")
    void contextNameIsTmux() {
        var injector = new TmuxContextInjector();
        assertEquals("tmux", injector.contextName());
    }

    @Test
    @DisplayName("null provider 抛异常")
    void nullProviderRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TmuxContextInjector(null));
    }

    @Test
    @DisplayName("register null assembler 不抛异常")
    void registerNullAssemblerSafe() {
        var injector = new TmuxContextInjector();
        assertDoesNotThrow(() -> injector.register(null));
    }
}
