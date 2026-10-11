package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.model.valobj.AroundWrapper;
import net.linlan.dsh.domain.tool.model.valobj.GuardDecision;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhase;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolPipeline — 五阶段工具管线")
class ToolPipelineTest {

    private static final ToolCallContext CTX = new ToolCallContext("testTool", Map.of(), "s1", "a1");

    @Test
    @DisplayName("无拦截器时直接执行主体")
    void noInterceptorsExecutesBody() {
        var pipeline = new ToolPipeline(List.of());
        var result = pipeline.execute(CTX, () -> ok("body-result"));
        assertTrue(result.content().get(0) instanceof TextBlock tb && tb.text().equals("body-result"));
    }

    @Test
    @DisplayName("五阶段按 PRE→GUARD→AROUND→POST→RESULT 顺序执行")
    void fivePhasesInOrder() {
        List<String> trace = new ArrayList<>();
        var pipeline = new ToolPipeline(List.of(
                preInterceptor(10, trace, "PRE"),
                guardInterceptor(10, trace, "GUARD"),
                aroundInterceptor(10, trace, "AROUND"),
                postInterceptor(10, trace, "POST"),
                resultInterceptor(10, trace, "RESULT")
        ));

        pipeline.execute(CTX, () -> { trace.add("BODY"); return ok("ok"); });

        assertEquals(List.of("PRE", "GUARD", "AROUND-enter", "BODY", "AROUND-exit", "POST", "RESULT"), trace);
    }

    @Test
    @DisplayName("GUARD 拒绝时主体不执行")
    void guardDenySkipsBody() {
        List<String> trace = new ArrayList<>();
        var pipeline = new ToolPipeline(List.of(
                preInterceptor(10, trace, "PRE"),
                new ToolPhaseInterceptor() {
                    @Override public ToolPhase phase() { return ToolPhase.GUARD; }
                    @Override public int priority() { return 10; }
                    @Override public GuardDecision guardIntercept(ToolCallContext ctx) {
                        trace.add("GUARD-DENY");
                        return GuardDecision.deny("not allowed", "FORBIDDEN");
                    }
                },
                aroundInterceptor(10, trace, "AROUND"),
                postInterceptor(10, trace, "POST")
        ));

        var result = pipeline.execute(CTX, () -> { trace.add("BODY"); return ok("ok"); });

        assertTrue(result.isError());
        assertFalse(trace.contains("BODY"));
        assertFalse(trace.contains("AROUND"));
        assertFalse(trace.contains("POST"));
    }

    @Test
    @DisplayName("AROUND 可短路不调用主体")
    void aroundCanShortCircuit() {
        var pipeline = new ToolPipeline(List.of(
                new ToolPhaseInterceptor() {
                    @Override public ToolPhase phase() { return ToolPhase.AROUND; }
                    @Override public int priority() { return 10; }
                    @Override public AroundWrapper aroundIntercept(ToolCallContext ctx) {
                        return proceed -> ok("around-shortcut");
                    }
                }
        ));

        var result = pipeline.execute(CTX, () -> ok("body"));
        var text = (TextBlock) result.content().get(0);
        assertEquals("around-shortcut", text.text());
    }

    @Test
    @DisplayName("多个 AROUND 按优先级嵌套（洋葱模型）")
    void multipleAroundsNest() {
        List<String> trace = new ArrayList<>();
        var pipeline = new ToolPipeline(List.of(
                aroundInterceptor(20, trace, "OUTER"),
                aroundInterceptor(10, trace, "INNER")
        ));

        pipeline.execute(CTX, () -> { trace.add("BODY"); return ok("ok"); });

        assertEquals(List.of("OUTER-enter", "INNER-enter", "BODY", "INNER-exit", "OUTER-exit"), trace);
    }

    @Test
    @DisplayName("PRE 短路时不执行后续阶段")
    void preShortCircuit() {
        List<String> trace = new ArrayList<>();
        var pipeline = new ToolPipeline(List.of(
                new ToolPhaseInterceptor() {
                    @Override public ToolPhase phase() { return ToolPhase.PRE; }
                    @Override public int priority() { return 10; }
                    @Override public ToolPhaseOutcome preIntercept(ToolCallContext ctx) {
                        trace.add("PRE-HALT");
                        return ToolPhaseOutcome.halt(ToolPhase.PRE, ok("halted"));
                    }
                },
                guardInterceptor(10, trace, "GUARD")
        ));

        var result = pipeline.execute(CTX, () -> ok("body"));
        var text = (TextBlock) result.content().get(0);
        assertEquals("halted", text.text());
        assertFalse(trace.contains("GUARD"));
    }

    @Test
    @DisplayName("RESULT 可改写最终结果")
    void resultCanRewrite() {
        var pipeline = new ToolPipeline(List.of(
                new ToolPhaseInterceptor() {
                    @Override public ToolPhase phase() { return ToolPhase.RESULT; }
                    @Override public int priority() { return 10; }
                    @Override public ToolExecutionResult resultIntercept(ToolCallContext ctx, ToolExecutionResult result) {
                        return ok("rewritten");
                    }
                }
        ));

        var result = pipeline.execute(CTX, () -> ok("original"));
        var text = (TextBlock) result.content().get(0);
        assertEquals("rewritten", text.text());
    }

    // ── helpers ──────────────────────────────────────────

    private static ToolExecutionResult ok(String text) {
        return ToolExecutionResult.ok(List.of(new TextBlock(text)));
    }

    private static ToolPhaseInterceptor preInterceptor(int priority, List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.PRE; }
            @Override public int priority() { return priority; }
            @Override public ToolPhaseOutcome preIntercept(ToolCallContext ctx) {
                trace.add(tag);
                return ToolPhaseOutcome.proceed(ToolPhase.PRE);
            }
        };
    }

    private static ToolPhaseInterceptor guardInterceptor(int priority, List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.GUARD; }
            @Override public int priority() { return priority; }
            @Override public GuardDecision guardIntercept(ToolCallContext ctx) {
                trace.add(tag);
                return GuardDecision.allow();
            }
        };
    }

    private static ToolPhaseInterceptor aroundInterceptor(int priority, List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.AROUND; }
            @Override public int priority() { return priority; }
            @Override public AroundWrapper aroundIntercept(ToolCallContext ctx) {
                return proceed -> {
                    trace.add(tag + "-enter");
                    var r = proceed.get();
                    trace.add(tag + "-exit");
                    return r;
                };
            }
        };
    }

    private static ToolPhaseInterceptor postInterceptor(int priority, List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.POST; }
            @Override public int priority() { return priority; }
            @Override public ToolPhaseOutcome postIntercept(ToolCallContext ctx, ToolExecutionResult result) {
                trace.add(tag);
                return ToolPhaseOutcome.proceed(ToolPhase.POST);
            }
        };
    }

    private static ToolPhaseInterceptor resultInterceptor(int priority, List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.RESULT; }
            @Override public int priority() { return priority; }
            @Override public ToolExecutionResult resultIntercept(ToolCallContext ctx, ToolExecutionResult result) {
                trace.add(tag);
                return result;
            }
        };
    }
}
