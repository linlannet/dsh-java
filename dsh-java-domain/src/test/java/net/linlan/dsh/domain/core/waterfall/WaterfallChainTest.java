package net.linlan.dsh.domain.core.waterfall;

import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;
import net.linlan.dsh.domain.core.waterfall.service.WaterfallChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaterfallChain — 拦截链执行")
class WaterfallChainTest {

    @Test
    @DisplayName("专家按 priority 升序执行")
    void expertsExecuteByPriority() {
        var chain = new WaterfallChain<>(List.of(
                expert(20, true, "second"),
                expert(10, true, "first"),
                expert(30, true, "third")
        ));

        var result = chain.execute(WaterfallEvent.of("test", ""), "");

        assertEquals("first→second→third", result);
    }

    @Test
    @DisplayName("可终止专家返回 Terminate 后后续不执行")
    void terminatingExpertStopsChain() {
        var chain = new WaterfallChain<>(List.of(
                expert(10, true, "a"),
                terminatingExpert(20, "b-STOP"),
                expert(30, true, "c")
        ));

        var result = chain.execute(WaterfallEvent.of("test", ""), "");

        assertEquals("a→b-STOP", result);
    }

    @Test
    @DisplayName("不可终止专家返回 Terminate 降级为 Continue")
    void nonTerminatingExpertDowngradesTerminate() {
        var chain = new WaterfallChain<>(List.of(
                expert(10, true, "a"),
                terminatingExpert(20, false, "b"),
                expert(30, true, "c")
        ));

        var result = chain.execute(WaterfallEvent.of("test", ""), "");

        assertEquals("a→b→c", result);
    }

    @Test
    @DisplayName("空专家链返回初始值")
    void emptyChainReturnsInitial() {
        var chain = new WaterfallChain<String>(List.of());
        var result = chain.execute(WaterfallEvent.of("test", "payload"), "initial");
        assertEquals("initial", result);
    }

    @Test
    @DisplayName("运行时注册专家后保持 priority 序")
    void registerExpertKeepsOrder() {
        var chain = new WaterfallChain<>(List.of(
                expert(10, true, "a"),
                expert(30, true, "c")
        ));
        chain.registerExpert(expert(20, true, "b"));

        assertEquals(3, chain.expertCount());
        var result = chain.execute(WaterfallEvent.of("test", ""), "");
        assertEquals("a→b→c", result);
    }

    @Test
    @DisplayName("每位专家可读取前序输出")
    void expertReceivesPreviousOutput() {
        var chain = new WaterfallChain<>(List.of(
                new WaterfallExpert<String>() {
                    @Override public int priority() { return 10; }
                    @Override public boolean canTerminate() { return false; }
                    @Override public WaterfallOutcome<String> handle(WaterfallEvent<String> event, String current) {
                        return WaterfallOutcome.continueWith(current + "[1]");
                    }
                },
                new WaterfallExpert<String>() {
                    @Override public int priority() { return 20; }
                    @Override public boolean canTerminate() { return false; }
                    @Override public WaterfallOutcome<String> handle(WaterfallEvent<String> event, String current) {
                        return WaterfallOutcome.continueWith(current + "[2]");
                    }
                }
        ));

        var result = chain.execute(WaterfallEvent.of("test", ""), "start");
        assertEquals("start[1][2]", result);
    }

    @Test
    @DisplayName("context 包含当前专家序号和已执行列表")
    void contextContainsExpertInfo() {
        var captured = new java.util.concurrent.atomic.AtomicReference<WaterfallEvent<String>>();
        var chain = new WaterfallChain<>(List.of(
                expert(10, true, "a"),
                new WaterfallExpert<String>() {
                    @Override public int priority() { return 20; }
                    @Override public boolean canTerminate() { return false; }
                    @Override public WaterfallOutcome<String> handle(WaterfallEvent<String> event, String current) {
                        captured.set(event);
                        return WaterfallOutcome.continueWith(current + "→b");
                    }
                }
        ));

        chain.execute(WaterfallEvent.of("test", ""), "");
        var ctx = captured.get().context();
        assertEquals(1, ctx.currentExpertIndex());
        assertTrue(ctx.executedExperts().size() >= 1);
    }

    // ── helpers ──────────────────────────────────────────

    private static WaterfallExpert<String> expert(int priority, boolean canTerminate, String tag) {
        return new WaterfallExpert<>() {
            @Override public int priority() { return priority; }
            @Override public boolean canTerminate() { return canTerminate; }
            @Override public WaterfallOutcome<String> handle(WaterfallEvent<String> event, String current) {
                return WaterfallOutcome.continueWith(current.isEmpty() ? tag : current + "→" + tag);
            }
        };
    }

    private static WaterfallExpert<String> terminatingExpert(int priority, String tag) {
        return terminatingExpert(priority, true, tag);
    }

    private static WaterfallExpert<String> terminatingExpert(int priority, boolean canTerminate, String tag) {
        return new WaterfallExpert<>() {
            @Override public int priority() { return priority; }
            @Override public boolean canTerminate() { return canTerminate; }
            @Override public WaterfallOutcome<String> handle(WaterfallEvent<String> event, String current) {
                var output = current.isEmpty() ? tag : current + "→" + tag;
                return WaterfallOutcome.terminateWith(output);
            }
        };
    }
}
