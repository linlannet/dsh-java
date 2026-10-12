package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.entity.Phase;
import net.linlan.dsh.domain.agent.model.valobj.AgentCancelCause;
import net.linlan.dsh.domain.agent.model.valobj.AgentStatus;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.agent.service.run.AgentRun;
import net.linlan.dsh.domain.agent.contract.outbound.AgentEventListener;
import net.linlan.dsh.domain.agent.service.Inbox;
import net.linlan.dsh.domain.agent.service.SystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.compaction.CompactionEngine;
import net.linlan.dsh.domain.agent.service.compaction.CompactionResult;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolCallBlock;
import net.linlan.dsh.domain.model.valobj.ToolResultBlock;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.llm.service.BlockAssembler;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.RequestHeaderPayload;
import net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.service.ToolCallExecutor;
import net.linlan.dsh.domain.core.scope.service.ScopeEventDispatcher;
import net.linlan.dsh.domain.session.checkpoint.service.CheckpointStrategy;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心 Agent 驱动器。
 * <p>
 * 执行流程：
 * 1. 收到 inbox 输入后唤醒；
 * 2. 打开 turn，流式调用 LLM 并执行工具；
 * 3. 直到模型不再请求工具后关闭 turn，循环处理直到 inbox 为空。
 * <p>
 * 该驱动器是线程安全的，所有阶段切换都在同步控制下完成，每次唤醒在虚拟线程中异步执行。
 *
 * <p>调用流程：模型输出 → 工具调用 → 结果回填 → 下一步模型调用；每一步写入会话事件。</p>
 * <p>示例：模型请求执行 fs_read 后，工具结果作为观察值进入下一轮模型输入。</p>
 *
 */
public class ReactLoopAgent implements AgentRun {

    private static final Logger log = LoggerFactory.getLogger(ReactLoopAgent.class);

    private final String id;
    private volatile AgentOptions options;
    private final String cwd;
    private final SessionLog session;
    private final Inbox inbox;
    private final ILlmRuntimePort llm;
    private final ToolRegistry toolRegistry;
    private final ToolCallExecutor toolExecutor;
    private final SystemPromptAssembler systemPromptAssembler;
    private final AgentEventListener events;
    private final CompactionEngine compactionEngine;
    private final java.util.function.Consumer<AgentOptions> optionsChangeListener;

    // ── 流式输出接收器（由 SSE 请求按次设置，用于实时推送增量） ──
    private volatile java.util.function.Consumer<String> streamDeltaSink;
    private volatile java.util.function.Consumer<String> streamReasoningSink;
    private volatile java.util.function.Consumer<ToolCallExecutor.ToolLifecycleEvent> streamToolEventSink;
    // ── 工具调用事件接收器（用于 SSE 推送 tool_call 事件，让前端分段） ──
    private volatile java.util.function.Consumer<ToolCallBlock> streamToolCallSink;
    private volatile Runnable streamFinishSink;
    private boolean continuationRequested;
    // ── 阶段状态 ──────────────────────────────────────────────
    private volatile Phase phase;
    private final AtomicReference<CompletableFuture<Void>> activityDone = new AtomicReference<>(CompletableFuture.completedFuture(null));

    // ── turn / step 计数器 ───────────────────────────────────────
    private volatile long turnCount = 0;

    // ── 释放标记 ────────────────────────────────────────────
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    // ── Scope 事件分发（可选，由 dsh.scope.enabled=true 注入） ──
    private volatile ScopeEventDispatcher scopeEventDispatcher;
    private volatile CheckpointStrategy checkpointStrategy;

    /**
     * 创建一个独立可驱动的 ReAct Agent。
     * <p>输入包含运行参数、工作目录、会话日志、消息 Inbox、LLM 端口、工具注册表和执行器；
     * 初始状态为 Idle。所有依赖都不允许为空，事件监听器为空时使用空实现。
     * <p>数据示例：agentId="a-1"，model="deepseek-chat"，cwd="/repo"，即可在该目录下驱动工具循环。
     */
    public ReactLoopAgent(
            String id,
            AgentOptions options,
            String cwd,
            SessionLog session,
            Inbox inbox,
            ILlmRuntimePort llm,
            ToolRegistry toolRegistry,
            ToolCallExecutor toolExecutor,
            SystemPromptAssembler systemPromptAssembler,
            AgentEventListener events,
            CompactionEngine compactionEngine
    ) {
        this(id, options, cwd, session, inbox, llm, toolRegistry, toolExecutor,
                systemPromptAssembler, events, compactionEngine, ignored -> {});
    }

    public ReactLoopAgent(
            String id,
            AgentOptions options,
            String cwd,
            SessionLog session,
            Inbox inbox,
            ILlmRuntimePort llm,
            ToolRegistry toolRegistry,
            ToolCallExecutor toolExecutor,
            SystemPromptAssembler systemPromptAssembler,
            AgentEventListener events,
            CompactionEngine compactionEngine,
            java.util.function.Consumer<AgentOptions> optionsChangeListener
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.options = Objects.requireNonNull(options, "options");
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        this.session = Objects.requireNonNull(session, "session");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
        this.systemPromptAssembler = Objects.requireNonNull(systemPromptAssembler, "systemPromptAssembler");
        this.events = events != null ? events : new NoopAgentEventListener();
        this.compactionEngine = Objects.requireNonNull(compactionEngine, "compactionEngine");
        this.optionsChangeListener = Objects.requireNonNull(optionsChangeListener, "optionsChangeListener");
        this.phase = new Phase.Idle(0);
        this.toolExecutor.setLifecycleListener(this::onToolLifecycleEvent);
    }

    // ── Agent 接口实现 ──────────────────────────────────────────

    /**
     * 返回 Agent 唯一标识。
     */
    @Override public String id() { return id; }
    @Override public AgentOptions options() { return options; }

    /**
     * 热更新模型、token 上限、provider 等运行参数。
     * <p>已存在的会话日志和工具注册表不变，新参数从下一个 LLM step 开始生效。
     */
    public void updateOptions(AgentOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        optionsChangeListener.accept(options);
    }
    @Override public SessionLog session() { return session; }
    @Override public Inbox inbox() { return inbox; }

    /**
     * 排空并返回本轮 Agent 期间工具写入的工件路径列表。
     * <p>委托给 ToolCallExecutor.drainArtifacts()，在 turn 结束时由上层统一收集。
     */
    @Override
    public List<String> drainWrittenArtifacts() {
        return toolExecutor.drainArtifacts();
    }

    /**
     * 返回 Agent 当前是否正在执行 ReAct 循环。
     * <p>数据示例：正在执行 turn 时返回 RUNNING；inbox 为空且无后台维护时返回 IDLE。
     */
    @Override
    public synchronized AgentStatus status() {
        return (phase instanceof Phase.Running) ? AgentStatus.RUNNING : AgentStatus.IDLE;
    }

    /**
     * 取消当前活动并按需清空待处理消息。
     * <p>执行流程：keepInbox=false 清空 Inbox -> 给 Running/Maintenance 阶段设置 abort 标记 ->
     * 发出 agent/cancelled 事件。正在执行的 LLM 请求或工具会在安全点停止。
     * <p>数据示例：用户点击“停止”，cause=UserCancel，keepInbox=false，当前回合结束后状态回到 IDLE。
     */
    @Override
    public synchronized void cancel(AgentCancelCause cause, boolean keepInbox) {
        log.info("[cancel] Agent={} 收到取消请求，cause={} keepInbox={}", id, cause, keepInbox);
        if (!keepInbox) {
            inbox.clear();
        }
        if (phase instanceof Phase.Running r) {
            r.abort().set(true);
        } else if (phase instanceof Phase.Maintenance m) {
            m.abort().set(true);
            if (!keepInbox) {
                m.wakeRequested().set(false);
            }
        }
        events.emit("agent/cancelled", Map.of("cause", cause, "keepInbox", keepInbox));
    }

    /**
     * 返回当前活动完成后的 Future。
     * <p>调用方可以用它等待 Agent 空闲；空闲状态下返回已完成 Future。
     */
    @Override
    public CompletableFuture<Void> whenIdle() {
        return activityDone.get().thenApply(v -> (Void) null);
    }

    /**
     * 安装实时增量接收器。
     * <p>
     * 执行流程：
     * 1. 由 SSE 控制器传入增量回调；
     * 2. Agent 在流式输出时实时推送文本片段；
     * 3. 传入 null 时清空接收器。
     */
    public void setStreamDeltaSink(java.util.function.Consumer<String> sink) {
        this.streamDeltaSink = sink;
    }

    public void setStreamReasoningSink(java.util.function.Consumer<String> sink) {
        this.streamReasoningSink = sink;
    }

    /**
     * 安装工具调用事件接收器。
     * <p>
     * Agent 在执行工具调用前会回调 sink 传入 ToolCallBlock，
     * 用于 SSE 流中推送 tool_call 事件让前端 UI 正确分段渲染。
     */
    public void setStreamToolCallSink(java.util.function.Consumer<ToolCallBlock> sink) {
        this.streamToolCallSink = sink;
    }

    public void setStreamToolEventSink(java.util.function.Consumer<ToolCallExecutor.ToolLifecycleEvent> sink) {
        this.streamToolEventSink = sink;
    }

    /**
     * 注入 Scope 事件分发器。
     * <p>当 {@code dsh.scope.enabled=true} 时由 Spring 配置注入；
     * 为 null 时所有 {@code dispatch*} 调用为空操作，行为退化为 v0.1.8 直接发布。
     */
    public void setScopeEventDispatcher(ScopeEventDispatcher dispatcher) {
        this.scopeEventDispatcher = dispatcher;
    }

    public void setCheckpointStrategy(CheckpointStrategy strategy) {
        this.checkpointStrategy = strategy;
    }

    @Override
    public void setStreamFinishSink(Runnable sink) {
        this.streamFinishSink = sink;
    }

    private void onToolLifecycleEvent(ToolCallExecutor.ToolLifecycleEvent event) {
        java.util.function.Consumer<ToolCallExecutor.ToolLifecycleEvent> sink = streamToolEventSink;
        if (sink == null) return;
        try { sink.accept(event); } catch (RuntimeException ignored) {}
    }

    /**
     * 在 Agent 空闲时执行压缩、重建等维护任务。
     * <p>执行流程：要求 Idle -> 切换到 Maintenance -> 执行任务 -> 无论成败恢复 Idle；
     * 维护期间收到唤醒请求时，完成后会重新驱动消息循环。
     * <p>数据示例：任务耗时 2 秒，期间新消息进入 Inbox，则维护结束后该消息会开启新 turn。
     */
    @Override
    public <T> CompletableFuture<T> runMaintenance(
            java.util.function.Function<AtomicBoolean, CompletableFuture<T>> task
    ) {
        Phase.Maintenance maintenance;
        synchronized (this) {
            if (!(phase instanceof Phase.Idle)) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("runMaintenance requires idle phase"));
            }
            maintenance = new Phase.Maintenance(turnCount);
            phase = maintenance;
        }
        AtomicBoolean abort = new AtomicBoolean(false);
        CompletableFuture<T> work = task.apply(abort);
        CompletableFuture<T> result = work.whenComplete((v, err) -> {
            synchronized (ReactLoopAgent.this) {
                phase = new Phase.Idle(turnCount);
                if (maintenance.wakeRequested().get() && inbox.hasPending()) {
                    wakeDriver(false);
                }
            }
        });
        // 记录当前 activity 的完成状态
        activityDone.set(result.thenApply(v -> (Void) null));
        return result;
    }

    /**
     * 把一条消息写入 Inbox，并按需唤醒驱动器。
     * <p>若 Agent 正在取消中，wakeup 消息会改排到 NEXT_TURN，避免与当前回合收尾竞争。
     * <p>数据示例：message=“继续”，target=CURRENT_TURN，wakeup=true，则当前回合结束后立即消费。
     */
    @Override
    public synchronized void send(Message message, InboxTarget target, boolean wakeup) {
        boolean wasAborting = false;
        if (phase instanceof Phase.Running r) {
            wasAborting = r.abort().get();
            if (wakeup && wasAborting) {
                // 唤醒后若仍处于中止态，则重新归类到下一轮处理。
                // 原始版本是在分片观察器里处理，这里因为输入箱追加会触发分片事件，所以直接在此处理。
                target = InboxTarget.NEXT_TURN;
            }
        }
        inbox.append(target, message);
        if (wakeup) {
            wakeDriver(wasAborting);
        }
    }

    // ── 驱动器 ───────────────────────────────────────────────────

    /**
     * 唤醒驱动器。
     * <p>
     * 执行流程：
     * 1. 如果当前空闲则立即开始处理；
     * 2. 如果正在中止或维护，则记录唤醒请求；
     * 3. 等待当前活动结束后再重放唤醒。
     */
    private synchronized void wakeDriver(boolean wakeAfterAbort) {
        if (disposed.get()) return;
        if (phase instanceof Phase.Idle) {
            if (!inbox.hasPending()) return;
            Phase.Running running = new Phase.Running(turnCount);
            phase = running;
            CompletableFuture<Void> activity = kick(running);
            activityDone.set(activity);
        } else if (phase instanceof Phase.Running r) {
            r.wakeRequested().set(true);
        } else if (phase instanceof Phase.Maintenance m) {
            m.wakeRequested().set(true);
        }
    }

    /**
     * 持续处理 turn，直到 inbox 为空。
     * <p>
     * 执行流程：
     * 1. 异步启动 turn 循环；
     * 2. 逐轮消费 inbox 中的消息；
     * 3. inbox 清空后进入空闲状态。
     */
    private CompletableFuture<Void> kick(Phase.Running running) {
        return CompletableFuture.runAsync(() -> {
            log.info("[kick] Agent={} 驱动器启动，开始消费 inbox（pending={})", id, inbox.hasPending());
            try {
                while (true) {
                    if (running.abort().get()) {
                        log.info("[kick] Agent={} 检测到取消信号，停止 turn 循环", id);
                        break;
                    }
                    if (!inbox.hasPending()) {
                        log.info("[kick] Agent={} inbox 已空，turn 循环自然结束", id);
                        break;
                    }
                    boolean shouldContinue = turn(running);
                    if (!shouldContinue) break;
                }
            } finally {
                synchronized (ReactLoopAgent.this) {
                    boolean wasAborted = running.abort().get();
                    turnCount = running.turn();
                    phase = new Phase.Idle(turnCount);
                    log.info("[kick] Agent={} 驱动器停机，共完成 {} 轮 turn，aborted={}",
                            id, turnCount, wasAborted);
                    if (!wasAborted && running.wakeRequested().get() && inbox.hasPending()) {
                        Phase.Running next = new Phase.Running(turnCount);
                        phase = next;
                        log.info("[kick] Agent={} 检测到 wake 请求且 inbox 仍有待处理消息，递归重入驱动器", id);
                        // 递归重入驱动器，继续处理后续 turn
                        CompletableFuture<Void> activity = kick(next);
                        activityDone.set(activity);
                    }
                }
            }
        }, java.util.concurrent.Executors.newCachedThreadPool());
    }

    // ── Turn 处理 ─────────────────────────────────────────────────────

    /**
     * 执行一个 turn。
     * <p>
     * 执行流程：
     * 1. 打开 turn；
     * 2. 逐步执行 step；
     * 3. 若仍有待处理消息则进入下一轮。
     */
    private boolean turn(Phase.Running running) {
        // 打开 turn / start
        long turn = running.turn() + 1;
        running = new Phase.Running(running.abort(), turn, 0, running.wakeRequested());
        // 更新阶段引用
        synchronized (this) { phase = running; }
        session.append(SessionEventFactory.turnStart());
        dispatchTurnEvent(turn, "turn/start", Map.of("turn", turn));
        log.info("[turn] ▶ Agent={} Turn#{} 开始（model={}）", id, turn, options.model());
        runCompaction();

        final long MAX_STEPS_PER_TURN = 50; // safety limit
        final int MAX_TOKEN_CONTINUATIONS = 4;
        int tokenContinuations = 0;
        // 仅 turn 的第一个 step 需要从 inbox 取用户消息；
        // 工具调用完成后的后续 step 应直接进入下一步（模型需要看到工具结果），
        // 不能因为 inbox 为空就退出循环。
        boolean midTurnContinuation = false;

        try {
            while (true) {
                if (running.step() >= MAX_STEPS_PER_TURN) {
                    continuationRequested = false;
                    log.warn("[turn] ⚠ Agent={} Turn#{} 达到单轮步数上限 {}，强制结束", id, turn, MAX_STEPS_PER_TURN);
                    session.append(SessionEventFactory.turnEnd(
                            new TurnEndReason.Error("max steps per turn exceeded", "MAX_STEPS")));
                    dispatchTurnEvent(turn, "turn/end", Map.of("reason", "max_steps"));
                    return false;
                }
                if (running.abort().get()) {
                    continuationRequested = false;
                    log.info("[turn] ⚠ Agent={} Turn#{} 检测到取消信号，中止 turn", id, turn);
                    session.append(SessionEventFactory.turnEnd(
                            new TurnEndReason.Aborted("aborted")));
                    dispatchTurnEvent(turn, "turn/end", Map.of("reason", "aborted"));
                    return false;
                }

                // 步骤前：抢占输入箱消息（仅 turn 的第一步需要）
                List<Message> claimed = List.of();
                if (!midTurnContinuation) {
                    claimed = inbox.claim(InboxTarget.NEXT_TURN, (int) turn);
                    if (claimed.isEmpty() && !inbox.hasPending() && !continuationRequested) {
                        // 没有消息，当前 turn 完成
                        break;
                    }
                    if (claimed.isEmpty() && !continuationRequested) {
                        // 没有下一轮消息，但可能还有待处理的下一步，
                        // 先检查是否可以继续执行 step。
                        List<Message> stepMessages = inbox.nextStep();
                        if (stepMessages.isEmpty()) break;
                    }
                }

                long step = running.step() + 1;
                running = new Phase.Running(running.abort(), turn, step, running.wakeRequested());
                synchronized (this) { phase = running; }

                // 步骤开始
                session.append(SessionEventFactory.stepStart(turn, step));
                dispatchStepEvent(turn, step, "step/start", Map.of("turn", turn, "step", step));

                // 追加从 inbox 抢占到的用户消息
                for (Message msg : claimed) {
                    session.append(SessionEventFactory.userMessage(turn, step, msg));
                    dispatchStepEvent(turn, step, "user/message", msg);
                }

                // 执行当前 step
                TurnEndReason stepResult = step(running, turn, step);

                // 步骤结束
                session.append(SessionEventFactory.stepEnd(turn, step));
                dispatchStepEvent(turn, step, "step/end", Map.of("turn", turn, "step", step));

                // 空值表示"继续执行下一步"（例如工具调用已完成，模型应继续推理）
                if (stepResult == null) {
                    midTurnContinuation = true;
                    log.debug("[turn] Agent={} Turn#{} Step#{} 工具调用完成，续步继续推理（midTurnContinuation）", id, turn, step);
                    continue;
                }

                // 检查当前 turn 是否应该结束
                if (stepResult instanceof TurnEndReason.Completed) {
                    continuationRequested = false;
                    log.info("[turn] ◀ Agent={} Turn#{} Step#{} 完成（模型无需更多工具调用）", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(new TurnEndReason.Completed()));
                    dispatchTurnEvent(turn, "turn/end", Map.of("reason", "completed"));
                    return true;
                } else if (stepResult instanceof TurnEndReason.MaxTokens) {
                    // 模型输出因 token 上限截断时，自动发起一次受控续写。
                    if (++tokenContinuations > MAX_TOKEN_CONTINUATIONS) {
                        log.warn("[turn] ⚠ Agent={} Turn#{} MaxTokens 续写次数超限({}/{})，结束 turn", id, turn, tokenContinuations - 1, MAX_TOKEN_CONTINUATIONS);
                        session.append(SessionEventFactory.turnEnd(new TurnEndReason.MaxTokens()));
                        dispatchTurnEvent(turn, "turn/end", Map.of("reason", "max_tokens"));
                        return true;
                    }
                    log.info("[turn] ↻ Agent={} Turn#{} Step#{} 输出被 token 上限截断，发起第 {}/{} 次受控续写", id, turn, step, tokenContinuations, MAX_TOKEN_CONTINUATIONS);
                    continuationRequested = true;
                    continue;
                } else if (stepResult instanceof TurnEndReason.Blocked) {
                    continuationRequested = false;
                    log.warn("[turn] ⚠ Agent={} Turn#{} Step#{} 被阻塞（Blocked）", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(new TurnEndReason.Blocked()));
                    dispatchTurnEvent(turn, "turn/end", Map.of("reason", "blocked"));
                    return false;
                } else if (stepResult instanceof TurnEndReason.Aborted) {
                    continuationRequested = false;
                    log.info("[turn] ⚠ Agent={} Turn#{} Step#{} 被中止（Aborted）", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(stepResult));
                    dispatchTurnEvent(turn, "turn/end", Map.of("reason", "aborted"));
                    return false;
                } else if (stepResult instanceof TurnEndReason.Error) {
                    continuationRequested = false;
                    log.error("[turn] ✗ Agent={} Turn#{} Step#{} 执行出错：{}", id, turn, step, ((TurnEndReason.Error) stepResult).message());
                    session.append(SessionEventFactory.turnEnd(stepResult));
                    dispatchTurnEvent(turn, "turn/end", Map.of("reason", "error"));
                    return false;
                }
            }

            continuationRequested = false;
            // 当前 turn 在没有工具调用的情况下完成
            log.info("[turn] ◀ Agent={} Turn#{} 自然完成（inbox 无更多待处理消息）", id, turn);
            session.append(SessionEventFactory.turnEnd(new TurnEndReason.Completed()));
            dispatchTurnEvent(turn, "turn/end", Map.of("reason", "completed"));
            return true;

        } catch (Exception e) {
            continuationRequested = false;
            log.error("[turn] ✗ Agent={} Turn#{} 发生未捕获异常：{}", id, turn, e.getMessage(), e);
            session.append(SessionEventFactory.turnEnd(
                    new TurnEndReason.Error(e.getMessage() != null ? e.getMessage() : e.toString(), "UNKNOWN")));
            dispatchTurnEvent(turn, "turn/end", Map.of("reason", "error"));
            return false;
        }
    }

    private void runCompaction() {
        try {
            CompactionResult result = compactionEngine
                    .compactIfNeeded(session, options, CompactionEngine.CompactionTrigger.PRESSURE)
                    .join();
            if (result != null) {
                log.info("[Compaction] Agent={} 上下文压缩完成，token {}→{}", id,
                        result.tokensBefore(), result.tokensAfter());
            }
        } catch (RuntimeException exception) {
            log.warn("[Compaction] Agent={} 上下文压缩失败：{}", id, exception.getMessage(), exception);
        }
    }

    // ── Step 处理 ─────────────────────────────────────────────────────

    /**
     * 执行一个 step。
     * <p>
     * 执行流程：
     * 1. 构建请求参数；
     * 2. 流式调用 LLM 并组装响应；
     * 3. 执行工具调用并返回 step 结束原因。
     */
    private TurnEndReason step(Phase.Running running, long turn, long step) {
        log.info("[step] ▷ Agent={} Turn#{} Step#{} 开始执行（model={}，工具数={}）",
                id, turn, step, options.model(), resolveToolSchemas().size());
        long stepStart = System.currentTimeMillis();
        try {
            // 1. 组装系统提示词
            long assembleStart = System.currentTimeMillis();
            SystemPromptAssembler.PromptAssembly assembly = systemPromptAssembler
                    .assemble(id, options, cwd, resolveToolSchemas())
                    .join();
            long assembleMs = System.currentTimeMillis() - assembleStart;
            if (assembleMs > 500) {
                log.info("[step] Agent={} Turn#{} Step#{} 系统提示词组装耗时={}ms", id, turn, step, assembleMs);
            }

            // 2. 构建请求参数
            GenerateOptions generateOptions = buildRequest(assembly, turn, step);

            // 3. 记录请求头（仅首次或配置变化时）
            if (step == 1 || headerChanged(assembly)) {
                session.append(SessionEventFactory.requestHeader(
                        Map.of("provider", options.provider(), "model", options.model()),
                        Map.of(),
                        assembly.system(),
                        assembly.tools().stream().map(ts -> Map.<String, Object>of("name", ts.name())).toList()
                ));
                session.append(SessionEventFactory.requestContext(
                        options.provider(), options.model(), null));
                dispatchStepEvent(turn, step, "request/header",
                        Map.of("provider", options.provider(), "model", options.model()));
            }

            // 检查点：LLM 请求前写入上下文快照
            if (checkpointStrategy != null) {
                try { checkpointStrategy.beforeModelRequest(session); } catch (RuntimeException ignored) {}
            }

            // 4. 流式调用 LLM —— 直接订阅适配器发布器
            BlockAssembler assembler = new BlockAssembler();
            List<SessionEvent> chunkEvents = new ArrayList<>();

            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> streamError = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean aborted = new AtomicBoolean(false);

            Flow.Publisher<StreamChunk> publisher = llm.stream(generateOptions);
            publisher.subscribe(new Flow.Subscriber<>() {
                /**
                 * on Subscribe。
                 * 流程：应用领域规则 → 更新领域对象 → 返回结果。
                 * @param s s参数。
                 */

                Flow.Subscription subscription;
                @Override public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                /**
                 * on Next。
                 * 流程：应用领域规则 → 更新领域对象 → 返回结果。
                 * @param chunk chunk参数。
                 */

                }
                @Override public void onNext(StreamChunk chunk) {
                    if (running.abort().get()) {
                        aborted.set(true);
                        subscription.cancel();
                        return;
                    }
                    if (chunk instanceof StreamChunk.Retry retry) {
                        Integer maximum = retry.maxAttempts();
                        String retryMessage = "模型请求失败，正在重试（"
                                + retry.attempt() + "/" + (maximum == null ? "∞" : maximum)
                                + "）：" + retry.failureMessage();
                        java.util.function.Consumer<String> sink = streamDeltaSink;
                        if (sink != null) {
                            try {
                                sink.accept(retryMessage);
                            } catch (RuntimeException ignored) {
                                // 重试提示发送失败不能打断当前 turn
                            }
                        }
                        return;
                    }
                    assembler.push(chunk);
                    if (chunk instanceof StreamChunk.ReasoningDelta rd) {
                        chunkEvents.add(SessionEventFactory.assistantChunk(turn, step, rd.text(), "reasoning-delta"));
                        java.util.function.Consumer<String> sink = streamReasoningSink;
                        if (sink != null) {
                            try { sink.accept(rd.text()); } catch (RuntimeException ignored) { }
                        }
                    }
                    if (chunk instanceof StreamChunk.TextDelta td) {
                        chunkEvents.add(SessionEventFactory.assistantChunk(turn, step, td.text(), "text-delta"));
                        java.util.function.Consumer<String> sink = streamDeltaSink;
                        if (sink != null) {
                            try {
                                sink.accept(td.text());
                            } catch (RuntimeException ignored) {
                                // 增量接收器失败不能打断当前 turn
                            }
                        }
                    }
                /**
                 * on Error。
                 * 流程：应用领域规则 → 更新领域对象 → 返回结果。
                 * @param t t参数。
                 */

                }
                @Override public void onError(Throwable t) {
                    streamError.set(t);
                    done.countDown();
                /**
                 * on Complete。
                 * 流程：应用领域规则 → 更新领域对象 → 返回结果。
                 */

                }
                @Override public void onComplete() {
                    done.countDown();
                }
            });

            // 等待流式结束，并检查是否被中止
            while (!done.await(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (running.abort().get()) {
                    aborted.set(true);
                    break;
                }
            }

            // 将 chunk 事件批量追加到 session log（避免逐条同步 JDBC 写入放大延迟）
            if (!chunkEvents.isEmpty()) {
                session.appendBatch(chunkEvents);
                dispatchStepEvent(turn, step, "assistant/chunk-batch", chunkEvents.size());
            }

            if (aborted.get()) {
                log.info("[step] ⚠ Agent={} Turn#{} Step#{} 流式输出期间被取消", id, turn, step);
                return new TurnEndReason.Aborted("aborted during stream");
            }

            if (streamError.get() != null) {
                Throwable err = streamError.get();
                log.error("[step] ✗ Agent={} Turn#{} Step#{} LLM 流式调用失败：{}",
                        id, turn, step, err.getMessage());
                return new TurnEndReason.Error(
                        err.getMessage() != null ? err.getMessage() : err.toString(),
                        "STREAM_ERROR");
            }

            // 5. 组装 assistant 消息
            FinishReason finish = assembler.finish();
            List<ContentBlock> blocks = assembler.blocks();
            TokenUsage usage = assembler.usage();
            log.debug("[step] Agent={} Turn#{} Step#{} LLM 流式结束，finish={}，blocks={}，usage={}",
                    id, turn, step, finish == null ? "null" : finish.getClass().getSimpleName(),
                    blocks.size(), usage);

            MessageSource.ModelMessageSource source = new MessageSource.ModelMessageSource(
                    options.provider(), options.model());
            Message assistantMsg = assembler.message(source);

            // 6. 检查结束原因 —— 先处理错误场景
            if (finish instanceof FinishReason.Error err) {
                log.error("[step] ✗ Agent={} Turn#{} Step#{} LLM 返回错误：code={} message={}",
                        id, turn, step, err.failure().code(), err.failure().message());
                // 如果 assistant 消息为空（例如 API Key 无效、网络异常），
                // 就替换成可见的错误消息，避免前端拿到空白结果。
                String errText = extractText(assistantMsg);
                if (errText == null || errText.isBlank()) {
                    String errMsg = "⚠️ LLM 调用失败: " + err.failure().message();
                    Message errorMsg = Message.createAssistant(
                            List.of(new TextBlock(errMsg)), source);
                    session.append(SessionEventFactory.assistantMessage(turn, step, errorMsg, usage));
                    dispatchStepEvent(turn, step, "assistant/message", errorMsg);
                } else {
                    // 如果已有部分内容，就记录已收到的片段
                    session.append(SessionEventFactory.assistantMessage(turn, step, assistantMsg, usage));
                    dispatchStepEvent(turn, step, "assistant/message", assistantMsg);
                }
                return new TurnEndReason.Error(err.failure().message(), err.failure().code());
            }

            // 非错误场景下，记录 assistant/message（surface event）
            session.append(SessionEventFactory.assistantMessage(turn, step, assistantMsg, usage));
            dispatchStepEvent(turn, step, "assistant/message", assistantMsg);

            if (finish instanceof FinishReason.MaxTokens) {
                log.info("[step] ↻ Agent={} Turn#{} Step#{} 输出达到 token 上限", id, turn, step);
                return new TurnEndReason.MaxTokens();
            }
            if (finish instanceof FinishReason.Aborted) {
                log.info("[step] ⚠ Agent={} Turn#{} Step#{} 流被中止", id, turn, step);
                return new TurnEndReason.Aborted("stream aborted");
            }

            // 7. 检查是否有工具调用
            List<ToolCallBlock> toolCalls = blocks.stream()
                    .filter(b -> b instanceof ToolCallBlock)
                    .map(b -> (ToolCallBlock) b)
                    .toList();

            if (toolCalls.isEmpty()) {
                log.info("[step] ◁ Agent={} Turn#{} Step#{} 完成，无工具调用，耗时={}ms",
                        id, turn, step, System.currentTimeMillis() - stepStart);
                Runnable finishSink = streamFinishSink;
                if (finishSink != null) {
                    try { finishSink.run(); } catch (RuntimeException ignored) {}
                }
                return new TurnEndReason.Completed();
            }

            // 8. 执行工具调用
            log.info("[step] 🔧 Agent={} Turn#{} Step#{} 模型请求 {} 个工具调用：{}",
                    id, turn, step, toolCalls.size(),
                    toolCalls.stream().map(ToolCallBlock::name).toList());
            // 推送 tool_call 事件给 SSE 前端，让前端分段显示多 step 文本
            java.util.function.Consumer<ToolCallBlock> tcSink = streamToolCallSink;
            if (tcSink != null) {
                for (ToolCallBlock tc : toolCalls) {
                    try { tcSink.accept(tc); } catch (RuntimeException ignored) {}
                }
            }
            log.info("[step] 🔧 Agent={} Turn#{} Step#{} 开始执行工具调用...", id, turn, step);
            // 检查点：工具副作用前写入可回滚快照
            if (checkpointStrategy != null) {
                for (ToolCallBlock tc : toolCalls) {
                    try {
                        checkpointStrategy.beforeToolSideEffect(session,
                                SessionEventFactory.toolCall(turn, step, "checkpoint", tc.name(), "{}"));
                    } catch (RuntimeException ignored) {}
                }
            }
            toolExecutor.execute(turn, step, toolCalls, running.abort(), null);
            log.info("[step] 🔧 Agent={} Turn#{} Step#{} 工具调用执行完成，耗时={}ms，续步推理",
                    id, turn, step, System.currentTimeMillis() - stepStart);

            // 9. 工具调用完成后继续下一步，让模型看到工具结果并继续推理。
            //    返回 null 表示“继续 step”，而不是当前 turn 结束。
            return null;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("[step] ⚠ Agent={} Turn#{} Step#{} 被中断", id, turn, step);
            return new TurnEndReason.Aborted("interrupted");
        } catch (Exception e) {
            log.error("[step] ✗ Agent={} Turn#{} Step#{} 执行异常：{}",
                    id, turn, step, e.getMessage(), e);
            return new TurnEndReason.Error(
                    e.getMessage() != null ? e.getMessage() : e.toString(),
                    "STEP_ERROR");
        }
    }

    // ── 请求构建 ─────────────────────────────────────────

    /**
     * 构建单步 LLM 生成参数。
     * <p>
     * 执行流程：
     * 1. 读取当前 session 历史；
     * 2. 按上下文预算裁剪消息；
     * 3. 组装 generate options。
     */
    private static final int CONTEXT_WINDOW_FALLBACK = 128_000;

    /**
     * 处理parseTemperature。
     * @return 领域处理结果。
     */
    private static final Double SAMPLING_TEMPERATURE = parseTemperature(
            System.getProperty("harness.agent.temperature",
                    System.getenv("HARNESS_AGENT_TEMPERATURE"))
    );

    /**
     * 解析可配置的采样温度。
     * <p>优先读取系统属性，其次读取环境变量；空值或非法数字返回 null，表示请求中不发送 temperature。
     * <p>数据示例：属性 harness.agent.temperature=0.2 返回 0.2；值为 "abc" 返回 null。
     */
    private static Double parseTemperature(String raw) {
        if (raw == null || raw.isBlank()) {
            return null; // omit temperature from the request
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建单次 LLM 请求参数。
     * <p>执行流程：折叠会话历史 -> 输出被截断时追加续写提示 -> 按 128K 上下文估算 token 并裁剪 ->
     * 合并系统提示词、工具 schema、模型参数和调用方凭据。
     * <p>数据示例：系统提示词约 2K token，maxTokens=4096，则历史消息预算约为 122K token。
     */
    private GenerateOptions buildRequest(SystemPromptAssembler.PromptAssembly assembly, long turn, long step) {
        List<Message> history = session.deriveMessages();
        if (continuationRequested) {
            history = new ArrayList<>(history);
            history.add(Message.createUser(
                    List.of(new TextBlock(CONTINUATION_PROMPT)),
                    new MessageSource.PluginSource("react-loop")
            ));
        }
        int maxContext = CONTEXT_WINDOW_FALLBACK;
        int systemTokens = estimateTokens(assembly.system());
        int budget = maxContext - systemTokens - options.maxTokens() - 1024;
        if (budget < 4096) budget = 4096;
        history = truncateToBudget(history, budget);
        return new GenerateOptions(
                options.provider(),
                options.model(),
                null,               // reasoningEffort
                history,
                assembly.system(),
                assembly.tools(),
                SAMPLING_TEMPERATURE, // null → omit; configurable via harness.agent.temperature
                options.maxTokens(),
                null,               // stop
                session.sessionId(), // sessionId
                null,               // purpose
                options.baseUrl(),
                options.apiKey(),
                options.protocol()
        );
    }

    static final String CONTINUATION_PROMPT =
            "The previous assistant response was cut off by the output token limit. "
                    + "Continue from the exact next character without repeating completed text, "
                    + "adding preamble, or restarting the answer. Preserve Markdown and code formatting.";

    /**
     * 按约 4 个字符等于 1 token 的粗略规则估算文本长度。
     * <p>该结果只用于上下文预算控制，不是上游模型的精确 tokenizer 结果。
     */
    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    /**
     * 汇总一条消息中正文、工具调用参数和工具结果的 token 估算值。
     * <p>消息本身额外加 4 token，用于近似对话结构开销。
     */
    private static int estimateTokens(Message msg) {
        int sum = 0;
        for (ContentBlock block : msg.content()) {
            if (block instanceof TextBlock tb) sum += estimateTokens(tb.text());
            else if (block instanceof ToolCallBlock tcb) {
                sum += estimateTokens(tcb.name()) + estimateTokens(tcb.arguments());
            } else if (block instanceof ToolResultBlock trb) {
                for (var inner : trb.content()) {
                    if (inner instanceof TextBlock tb) sum += estimateTokens(tb.text());
                }
            }
        }
        return sum + 4;
    }

    /**
     * 按 token 预算从后往前保留会话历史。
     * <p>执行流程：先估算全量历史；超预算时从最新消息向前累加；
     * 若一条也放不下，则至少保留最后 6 条，保证模型仍有最近上下文。
     * <p>数据示例：预算 100 token，最后三条为 80/10/10 token，则保留最后三条，最早的旧消息被丢弃。
     */
    private static List<Message> truncateToBudget(List<Message> history, int tokenBudget) {
        if (history.isEmpty()) return history;
        int total = 0;
        for (Message msg : history) total += estimateTokens(msg);
        if (total <= tokenBudget) return history;

        List<Message> kept = new ArrayList<>();
        int keptTokens = 0;
        int toolResultsSeen = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            int msgTokens = estimateTokens(msg);
            if (keptTokens + msgTokens > tokenBudget) break;
            kept.add(0, msg);
            keptTokens += msgTokens;
        }

        int minContext = Math.min(6, history.size());
        if (kept.size() < minContext) {
            kept.clear();
            for (int i = history.size() - minContext; i < history.size(); i++) {
                kept.add(history.get(i));
            }
        }
        return java.util.Collections.unmodifiableList(kept);
    }

    /**
     * 检查请求头是否发生变化。
     * <p>
     * 执行流程：
     * 1. 读取折叠后的请求头；
     * 2. 与当前 assembly 的系统提示词比较；
     * 3. 返回是否需要重新记录请求头。
     */
    private boolean headerChanged(SystemPromptAssembler.PromptAssembly assembly) {
        RequestHeaderPayload folded = session.requestHeader();
        if (folded == null) return true;
        return !java.util.Objects.equals(folded.system(), assembly.system());
    }

    /**
     * 解析可启用的工具 schema。
     * <p>
     * 执行流程：
     * 1. 向工具注册表请求全部 schema；
     * 2. 按权限或画像过滤可用工具；
     * 3. 返回最终工具列表。
     */
    private List<ToolSchema> resolveToolSchemas() {
        // 这里直接委托工具注册表返回已注册工具；
        // 原始版本会再结合权限进行过滤。
        return toolRegistry.schemas();
    }

    // ── Scope 事件分发辅助 ────────────────────────────────────

    /**
     * 在 turn 级别分发 scope 事件。
     * <p>scope 层级：{@code session:<id> → turn:<turn>}。
     * dispatcher 为 null 时空操作。
     */
    private void dispatchTurnEvent(long turn, String type, Object payload) {
        ScopeEventDispatcher dispatcher = this.scopeEventDispatcher;
        if (dispatcher == null) return;
        dispatcher.dispatch(ScopedEvent.of(
                ScopeKey.child(ScopeKey.root("session:" + id), "turn:" + turn),
                type, payload));
    }

    /**
     * 在 step 级别分发 scope 事件。
     * <p>scope 层级：{@code session:<id> → turn:<turn> → step:<step>}。
     * dispatcher 为 null 时空操作。
     */
    private void dispatchStepEvent(long turn, long step, String type, Object payload) {
        ScopeEventDispatcher dispatcher = this.scopeEventDispatcher;
        if (dispatcher == null) return;
        dispatcher.dispatch(ScopedEvent.of(
                ScopeKey.child(ScopeKey.child(ScopeKey.root("session:" + id), "turn:" + turn), "step:" + step),
                type, payload));
    }

    // ── 辅助方法 ─────────────────────────────────────────────

    /**
     * 从消息内容块中提取纯文本。
     * <p>
     * 执行流程：
     * 1. 遍历内容块；
     * 2. 收集其中的文本块；
     * 3. 拼接为纯文本字符串。
     */
    private static String extractText(Message message) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            }
        }
        return sb.toString();
    }

    // ── 释放 ──────────────────────────────────────────────────

    /**
     * 释放 Agent。
     * <p>
     * 执行流程：
     * 1. 标记 Agent 已释放；
     * 2. 取消当前活动；
     * 3. 等待系统进入静止状态。
     */
    public CompletableFuture<Void> dispose() {
        disposed.set(true);
        cancel(new AgentCancelCause.Disposed(), false);
        return whenIdle();
    }

    // ── T1.019: failed-step 恢复 ───────────────────────────────

    /**
     * 检测会话中是否有 failed-step 并返回恢复信息。
     * @return 恢复信息（null 表示无 failed-step）
     */
    public FailedStepRecovery detectFailedStep() {
        var events = session.events();
        long lastStepStartSeq = -1;
        long lastStepStartTurn = -1;
        long lastStepStartStep = -1;

        for (var event : events) {
            if (event instanceof SessionEvent.StepStart ss) {
                lastStepStartSeq = event.seq();
                lastStepStartTurn = ss.turn();
                lastStepStartStep = ss.step();
            } else if (event instanceof SessionEvent.TurnEnd te) {
                if (te.reason() instanceof TurnEndReason.Error) {
                    return new FailedStepRecovery(
                            lastStepStartSeq, lastStepStartTurn, lastStepStartStep, true);
                }
            }
        }
        return null;
    }

    /**
     * FailedStep 恢复信息。
     * @param seq 失败步骤的序列号
     * @param turn 失败步骤所属的 turn
     * @param step 失败步骤的 step 号
     * @param recoverable 是否可恢复
     */
    public record FailedStepRecovery(long seq, long turn, long step, boolean recoverable) {}

    // ── 空实现监听器 ────────────────────────────────────────────

    /**
     * NoopAgentEventListener 定义当前模块中的class契约与职责。
     */
    private static final class NoopAgentEventListener implements AgentEventListener {
        @Override public void emit(String type, Object payload) {}
        @Override public CompletableFuture<Void> serial(String type, Object payload) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public <T> CompletableFuture<T> waterfall(String type, Object payload, CompletableFuture<T> defaultAction) {
            return defaultAction;
        }
    }
}
