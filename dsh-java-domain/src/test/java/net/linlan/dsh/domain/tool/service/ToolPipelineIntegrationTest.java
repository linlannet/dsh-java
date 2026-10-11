package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.agent.service.run.tool.InMemoryToolRegistry;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.tool.model.valobj.AroundWrapper;
import net.linlan.dsh.domain.tool.model.valobj.GuardDecision;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhase;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolPipeline 集成测试 — 五阶段管线与 ToolCallExecutor 集成")
class ToolPipelineIntegrationTest {

    @Test
    @DisplayName("全五阶段按序执行（PRE→GUARD→AROUND→POST→RESULT）")
    void fullFivePhasesInOrder() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.addPhaseInterceptor(preSpy(trace, "PRE"));
        executor.addPhaseInterceptor(guardSpy(trace, "GUARD"));
        executor.addPhaseInterceptor(aroundSpy(trace, "AROUND"));
        executor.addPhaseInterceptor(postSpy(trace, "POST"));
        executor.addPhaseInterceptor(resultSpy(trace, "RESULT"));

        executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        assertTrue(trace.contains("PRE"), "PRE should execute");
        assertTrue(trace.contains("GUARD"), "GUARD should execute");
        assertTrue(trace.contains("AROUND-enter"), "AROUND should enter");
        assertTrue(trace.contains("BODY"), "BODY should execute");
        assertTrue(trace.contains("AROUND-exit"), "AROUND should exit");
        assertTrue(trace.contains("POST"), "POST should execute");
        assertTrue(trace.contains("RESULT"), "RESULT should execute");

        int preIdx = trace.indexOf("PRE");
        int guardIdx = trace.indexOf("GUARD");
        int aroundEnterIdx = trace.indexOf("AROUND-enter");
        int bodyIdx = trace.indexOf("BODY");
        int aroundExitIdx = trace.indexOf("AROUND-exit");
        int postIdx = trace.indexOf("POST");
        int resultIdx = trace.indexOf("RESULT");

        assertTrue(preIdx < guardIdx, "PRE before GUARD");
        assertTrue(guardIdx < aroundEnterIdx, "GUARD before AROUND-enter");
        assertTrue(aroundEnterIdx < bodyIdx, "AROUND-enter before BODY");
        assertTrue(bodyIdx < aroundExitIdx, "BODY before AROUND-exit");
        assertTrue(aroundExitIdx < postIdx, "AROUND-exit before POST");
        assertTrue(postIdx < resultIdx, "POST before RESULT");
    }

    @Test
    @DisplayName("GUARD 拒绝时主体不执行")
    void guardDenySkipsBody() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.addPhaseInterceptor(new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.GUARD; }
            @Override public int priority() { return 10; }
            @Override public GuardDecision guardIntercept(ToolCallContext ctx) {
                trace.add("GUARD-DENY");
                return GuardDecision.deny("forbidden", "FORBIDDEN");
            }
        });
        executor.addPhaseInterceptor(aroundSpy(trace, "AROUND"));

        executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        assertTrue(trace.contains("GUARD-DENY"), "GUARD should deny");
        assertFalse(trace.contains("BODY"), "Body should not execute");
        assertFalse(trace.contains("AROUND"), "AROUND should not execute");
    }

    @Test
    @DisplayName("AROUND 可短路不调用主体")
    void aroundCanShortCircuit() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.addPhaseInterceptor(new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.AROUND; }
            @Override public int priority() { return 10; }
            @Override public AroundWrapper aroundIntercept(ToolCallContext ctx) {
                return proceed -> ToolExecutionResult.ok(List.of(new TextBlock("around-shortcut")));
            }
        });

        executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        assertFalse(trace.contains("BODY"), "Body should not execute when AROUND short-circuits");
    }

    @Test
    @DisplayName("多个 AROUND 按优先级嵌套（洋葱模型）")
    void multipleAroundsNest() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.addPhaseInterceptor(aroundSpy(20, trace, "OUTER"));
        executor.addPhaseInterceptor(aroundSpy(10, trace, "INNER"));

        executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        assertEquals(List.of("OUTER-enter", "INNER-enter", "BODY", "INNER-exit", "OUTER-exit"), trace);
    }

    @Test
    @DisplayName("无额外拦截器时退化为原有直接执行路径（v0.1.8 回归）")
    void noExtraInterceptorsDegradesToDirectPath() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        boolean concluded = executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        assertTrue(trace.contains("BODY"), "Body should execute directly");
        assertFalse(concluded, "echo does not conclude turn");
    }

    @Test
    @DisplayName("RESULT 拦截器可改写最终结果")
    void resultInterceptorCanRewrite() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.addPhaseInterceptor(new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.RESULT; }
            @Override public int priority() { return 10; }
            @Override public ToolExecutionResult resultIntercept(ToolCallContext ctx, ToolExecutionResult result) {
                return ToolExecutionResult.ok(List.of(new TextBlock("rewritten")));
            }
        });

        executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        var events = session.events();
        boolean foundRewritten = false;
        for (var event : events) {
            if (event instanceof SessionEvent.ToolResult tr) {
                var blocks = tr.data().message().content();
                for (var block : blocks) {
                    if (block instanceof net.linlan.dsh.domain.model.valobj.ToolResultBlock trb) {
                        for (var inner : trb.content()) {
                            if (inner instanceof TextBlock tb && tb.text().equals("rewritten")) {
                                foundRewritten = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(foundRewritten, "RESULT interceptor should rewrite to 'rewritten'");
    }

    @Test
    @DisplayName("并行模式下结果按调用顺序回填")
    void parallelModePreservesOrder() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(parallelEchoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 4);

        executor.addPhaseInterceptor(aroundSpy(10, trace, "AROUND"));

        var calls = List.of(
                new ToolCallBlock("call_1", "echo", "{\"i\":1}"),
                new ToolCallBlock("call_2", "echo", "{\"i\":2}"),
                new ToolCallBlock("call_3", "echo", "{\"i\":3}")
        );

        executor.execute(1, 1, calls, new AtomicBoolean(false), msg -> {});

        assertEquals(3, trace.stream().filter(s -> s.equals("BODY")).count(), "All 3 tools should execute");
        assertEquals(3, trace.stream().filter(s -> s.equals("AROUND-enter")).count(), "All 3 AROUND-enter");
        assertEquals(3, trace.stream().filter(s -> s.equals("AROUND-exit")).count(), "All 3 AROUND-exit");
    }

    @Test
    @DisplayName("PRE 短路时不执行后续阶段")
    void preShortCircuitSkipsRest() {
        List<String> trace = new ArrayList<>();
        var registry = new InMemoryToolRegistry();
        registry.register(echoTool("echo", trace));
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.addPhaseInterceptor(new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.PRE; }
            @Override public int priority() { return 10; }
            @Override public ToolPhaseOutcome preIntercept(ToolCallContext ctx) {
                trace.add("PRE-HALT");
                return ToolPhaseOutcome.halt(ToolPhase.PRE,
                        ToolExecutionResult.ok(List.of(new TextBlock("halted"))));
            }
        });
        executor.addPhaseInterceptor(guardSpy(trace, "GUARD"));
        executor.addPhaseInterceptor(aroundSpy(trace, "AROUND"));

        executor.execute(1, 1, List.of(call("echo")), new AtomicBoolean(false), msg -> {});

        assertTrue(trace.contains("PRE-HALT"), "PRE should halt");
        assertFalse(trace.contains("GUARD"), "GUARD should not execute");
        assertFalse(trace.contains("AROUND"), "AROUND should not execute");
        assertFalse(trace.contains("BODY"), "Body should not execute");
    }

    // ── helpers ──────────────────────────────────────────

    private static SessionLog newSession() {
        var header = SessionHeader.create("test-session", Instant.EPOCH, null, null);
        return new SessionLog("test-session", header);
    }

    private static ToolCallBlock call(String toolName) {
        return new ToolCallBlock("call_1", toolName, "{}");
    }

    private static ToolDefinition echoTool(String name, List<String> trace) {
        return new ToolDefinition() {
            @Override public String name() { return name; }
            @Override public String description() { return "echo test tool"; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override
            public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
                trace.add("BODY");
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.ok(List.of(new TextBlock("ok"))));
            }
        };
    }

    private static ToolDefinition parallelEchoTool(String name, List<String> trace) {
        return new ToolDefinition() {
            @Override public String name() { return name; }
            @Override public String description() { return "parallel echo test tool"; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public boolean isConcurrencySafe(Object args) { return true; }
            @Override
            public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
                trace.add("BODY");
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.ok(List.of(new TextBlock("ok"))));
            }
        };
    }

    private static ToolPhaseInterceptor preSpy(List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.PRE; }
            @Override public int priority() { return 10; }
            @Override public ToolPhaseOutcome preIntercept(ToolCallContext ctx) {
                trace.add(tag);
                return ToolPhaseOutcome.proceed(ToolPhase.PRE);
            }
        };
    }

    private static ToolPhaseInterceptor guardSpy(List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.GUARD; }
            @Override public int priority() { return 10; }
            @Override public GuardDecision guardIntercept(ToolCallContext ctx) {
                trace.add(tag);
                return GuardDecision.allow();
            }
        };
    }

    private static ToolPhaseInterceptor aroundSpy(List<String> trace, String tag) {
        return aroundSpy(10, trace, tag);
    }

    private static ToolPhaseInterceptor aroundSpy(int priority, List<String> trace, String tag) {
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

    private static ToolPhaseInterceptor postSpy(List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.POST; }
            @Override public int priority() { return 10; }
            @Override public ToolPhaseOutcome postIntercept(ToolCallContext ctx, ToolExecutionResult result) {
                trace.add(tag);
                return ToolPhaseOutcome.proceed(ToolPhase.POST);
            }
        };
    }

    private static ToolPhaseInterceptor resultSpy(List<String> trace, String tag) {
        return new ToolPhaseInterceptor() {
            @Override public ToolPhase phase() { return ToolPhase.RESULT; }
            @Override public int priority() { return 10; }
            @Override public ToolExecutionResult resultIntercept(ToolCallContext ctx, ToolExecutionResult result) {
                trace.add(tag);
                return result;
            }
        };
    }
}
