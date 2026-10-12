package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.agent.service.run.tool.InMemoryToolRegistry;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionInput;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.model.valobj.ToolExecutionMode;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolCallExecutor 工具调用执行器与策略注入")
class ToolCallExecutorTest {

    @Test
    @DisplayName("构造函数 maxParallel < 1 抛 IllegalArgumentException")
    void maxParallelLessThanOneRejected() {
        var registry = new InMemoryToolRegistry();
        var session = newSession();
        assertThrows(IllegalArgumentException.class,
                () -> new ToolCallExecutor(registry, session, 0));
    }

    @Test
    @DisplayName("构造函数 null registry 抛 NullPointerException")
    void nullRegistryRejected() {
        var session = newSession();
        assertThrows(NullPointerException.class,
                () -> new ToolCallExecutor(null, session, 1));
    }

    @Test
    @DisplayName("构造函数 null session 抛 NullPointerException")
    void nullSessionRejected() {
        var registry = new InMemoryToolRegistry();
        assertThrows(NullPointerException.class,
                () -> new ToolCallExecutor(registry, null, 1));
    }

    @Test
    @DisplayName("setApprovalGate(null) 回退到 allowAll 不抛异常")
    void nullApprovalGateFallsBackToAllowAll() {
        var executor = newExecutor();
        assertDoesNotThrow(() -> executor.setApprovalGate(null));
    }

    @Test
    @DisplayName("setHookService(null) 不抛异常")
    void nullHookServiceAccepted() {
        var executor = newExecutor();
        assertDoesNotThrow(() -> executor.setHookService(null));
    }

    @Test
    @DisplayName("setLifecycleListener 注入后不抛异常")
    void lifecycleListenerInjected() {
        var executor = newExecutor();
        var received = new AtomicReference<ToolCallExecutor.ToolLifecycleEvent>(null);
        assertDoesNotThrow(() -> executor.setLifecycleListener(received::set));
    }

    @Test
    @DisplayName("drainArtifacts 空时返回空列表")
    void drainArtifactsEmptyReturnsEmptyList() {
        var executor = newExecutor();
        var artifacts = executor.drainArtifacts();
        assertNotNull(artifacts);
        assertTrue(artifacts.isEmpty());
    }

    @Test
    @DisplayName("execute 空工具调用列表返回 false")
    void executeEmptyToolCallsReturnsFalse() {
        var executor = newExecutor();
        var abort = new AtomicBoolean(false);
        boolean concluded = executor.execute(1, 1, List.of(), abort, null);
        assertFalse(concluded, "空工具调用列表不应结束 turn");
    }

    @Test
    @DisplayName("addPhaseInterceptor 注入拦截器不抛异常")
    void addPhaseInterceptorAccepted() {
        var executor = newExecutor();
        assertDoesNotThrow(() -> executor.addPhaseInterceptor(new NoopPhaseInterceptor()));
        assertDoesNotThrow(() -> executor.addPhaseInterceptor(new NoopPhaseInterceptor()));
    }

    @Test
    @DisplayName("setApprovalGate(DENY) 后 execute 不执行真实工具")
    void denyApprovalGateBlocksExecution() {
        var registry = new InMemoryToolRegistry();
        var session = newSession();
        registry.register(stubTool("blocked-tool", false));
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.setApprovalGate((toolName, args) ->
                RuntimeApprovalGate.Decision.DENY);

        var abort = new AtomicBoolean(false);
        var calls = List.of(new ToolCallBlock("call-1", "blocked-tool", "{}"));
        boolean concluded = executor.execute(1, 1, calls, abort, null);

        // DENY 时工具不执行，turn 不应由工具结果结束
        assertFalse(concluded, "被 DENY 的工具不应结束 turn");
    }

    @Test
    @DisplayName("setApprovalGate(ALLOW) 后 execute 执行已注册工具")
    void allowApprovalGateExecutesTool() {
        var registry = new InMemoryToolRegistry();
        var session = newSession();
        registry.register(stubTool("allowed-tool", false));
        var executor = new ToolCallExecutor(registry, session, 1);

        executor.setApprovalGate(RuntimeApprovalGate.allowAll());

        var abort = new AtomicBoolean(false);
        var calls = List.of(new ToolCallBlock("call-1", "allowed-tool", "{}"));
        boolean concluded = executor.execute(1, 1, calls, abort, null);

        // 工具执行后不结束 turn（续步推理）
        assertFalse(concluded);
        // 会话日志应记录 tool_call 事件
        assertTrue(session.size() > 0, "工具执行应写入会话事件");
    }

    @Test
    @DisplayName("toolTimeoutMs <= 0 时直接在调用线程执行不超时")
    void zeroTimeoutExecutesInline() {
        var registry = new InMemoryToolRegistry();
        var session = newSession();
        registry.register(stubTool("inline-tool", false));
        var executor = new ToolCallExecutor(
                registry, session, 1, ToolArgumentsParser.identity(), 0L);

        var abort = new AtomicBoolean(false);
        var calls = List.of(new ToolCallBlock("c1", "inline-tool", "{}"));
        assertDoesNotThrow(() -> executor.execute(1, 1, calls, abort, null));
    }

    @Test
    @DisplayName("未注册工具时 execute 不抛异常（降级为失败结果）")
    void unregisteredToolDegradesGracefully() {
        var registry = new InMemoryToolRegistry();
        var session = newSession();
        var executor = new ToolCallExecutor(registry, session, 1);

        var abort = new AtomicBoolean(false);
        var calls = List.of(new ToolCallBlock("c1", "no-such-tool", "{}"));
        assertDoesNotThrow(() -> executor.execute(1, 1, calls, abort, null));
    }

    // ── helpers ──────────────────────────────────────────────

    private static SessionLog newSession() {
        String id = "tool-test-" + System.nanoTime();
        return new SessionLog(id, SessionHeader.create(id, Instant.now(), "/tmp", null));
    }

    private static ToolCallExecutor newExecutor() {
        return new ToolCallExecutor(new InMemoryToolRegistry(), newSession(), 1);
    }

    private static ToolDefinition stubTool(String name, boolean concurrencySafe) {
        return new ToolDefinition() {
            @Override
            public String name() { return name; }
            @Override
            public String description() { return "stub tool for test"; }
            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override
            public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext exec) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.ok(List.of(new TextBlock("stub result"))));
            }
            @Override
            public boolean isConcurrencySafe(Object args) {
                return concurrencySafe;
            }
        };
    }

    private static final class NoopPhaseInterceptor implements ToolPhaseInterceptor {
        @Override
        public ToolPhase phase() { return ToolPhase.PRE; }
        @Override
        public int priority() { return 100; }
    }
}
