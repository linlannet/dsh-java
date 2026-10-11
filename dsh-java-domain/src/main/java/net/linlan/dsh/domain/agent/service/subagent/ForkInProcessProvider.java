package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.agent.service.run.AgentRun;
import net.linlan.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.TurnEndReason;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * ForkInProcessProvider 表示进程内复制子智能体的领域提供者。
 */
public class ForkInProcessProvider implements SubagentProvider {

    private final String providerName;
    private final SubagentRegistry registry;
    private final AgentRunLifecycle agentFactory;
    private final Supplier<AgentOptions> defaultOptionsSupplier;

    /**
     * 初始化进程内 Fork 子智能体提供者。
     * <p>流程：继承当前 Agent 上下文 → 派生独立会话 → 回传执行结果。</p>
     */
    public ForkInProcessProvider(
            String providerName,
            SubagentRegistry registry,
            AgentRunLifecycle agentFactory,
            Supplier<AgentOptions> defaultOptionsSupplier
    ) {
        this.providerName = providerName;
        this.registry = registry;
        this.agentFactory = agentFactory;
        this.defaultOptionsSupplier = Objects.requireNonNull(defaultOptionsSupplier, "defaultOptionsSupplier");
    }
    /**
     * 返回 name。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public String name() {
        return providerName;
    }
    /**
     * 返回 capabilities。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public SubagentCapabilities capabilities() {
        return new SubagentCapabilities(true, true, true);
    }
    /**
     * 启动目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param request 请求对象。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<SubagentResult> start(SubagentStartRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String childSessionId = "child-" + UUID.randomUUID();
            AgentOptions childOptions = request.options() != null
                    ? request.options()
                    : Objects.requireNonNull(defaultOptionsSupplier.get(), "defaultOptionsSupplier returned null");

            // Create child agent with fresh session
            AgentRun childAgent = agentFactory.create(
                    childSessionId,
                    childOptions,
                    request.parentCwd() != null ? request.parentCwd() : "."
            );

            // Deliver the prompt as a user message
            List<ContentBlock> promptBlocks = List.of(
                    new TextBlock(request.prompt() != null && !request.prompt().isEmpty()
                            ? request.prompt() : "(empty task)")
            );
            Message userMessage = Message.createUser(promptBlocks, new MessageSource.UserSource());
            childAgent.send(userMessage, InboxTarget.NEXT_TURN, true);

            // Wait for the turn to complete
            childAgent.whenIdle().join();

            // Collect the last assistant message
            List<ContentBlock> output = collectLastAssistantOutput(childAgent);

            // Determine stop reason
            String stopReason = determineStopReason(childAgent);

            // Dispose the child agent
            childAgent.dispose();

            return new SubagentResult(stopReason, output, registry.nextRunId());
        });
    }

    /**
     * 处理completedTurnPrefix轮次。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param parentSession parentSession会话。
     * @return 领域处理结果。
     */
    public static List<SessionEvent> completedTurnPrefix(SessionLog parentSession) {
        List<SessionEvent> events = parentSession.events();
        int lastEndIdx = -1;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof SessionEvent.TurnEnd) {
                lastEndIdx = i;
                break;
            }
        }
        if (lastEndIdx < 0) return List.of();
        return List.copyOf(events.subList(0, lastEndIdx + 1));
    }

    /**
     * 处理collect Last Assistant Output。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agent Agent参数。
     * @return 方法执行结果。
     */
    private List<ContentBlock> collectLastAssistantOutput(AgentRun agent) {
        var events = agent.session().events();
        TextBlock lastText = null;
        for (var event : events) {
            if (event instanceof SessionEvent.AssistantMessage am) {
                var msg = am.data().message();
                if (msg != null && msg.content() != null) {
                    for (var block : msg.content()) {
                        if (block instanceof TextBlock tb) {
                            lastText = tb;
                        }
                    }
                }
            }
        }
        return lastText != null ? List.of(lastText) : List.of();
    }

    /**
     * 处理determine Stop Reason。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param agent Agent参数。
     * @return 方法执行结果。
     */
    private String determineStopReason(AgentRun agent) {
        var events = agent.session().events();
        TurnEndReason lastReason = null;
        for (var event : events) {
            if (event instanceof SessionEvent.TurnEnd te) {
                lastReason = te.reason();
            }
        }
        return SpawnInProcessProvider.mapStopReason(lastReason);
    }
}
