package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import net.linlan.dsh.domain.agent.contract.outbound.AgentEventListener;
import net.linlan.dsh.domain.agent.contract.outbound.RuntimeApprovalGateway;
import net.linlan.dsh.domain.agent.contract.outbound.UserQuestionGateway;
import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.ApprovalModeVO;
import net.linlan.dsh.domain.agent.service.Inbox;
import net.linlan.dsh.domain.agent.service.LoggingInboxNotifications;
import net.linlan.dsh.domain.agent.service.SystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.run.tool.AgentToolCatalog;
import net.linlan.dsh.domain.agent.service.run.tool.AgentToolCatalog.AgentToolContext;
import net.linlan.dsh.domain.agent.service.compaction.CompactionEngine;
import net.linlan.dsh.domain.hooks.IHookService;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.task.permission.adapter.port.IPermissionMatrixPort;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.service.MatrixRuntimeApprovalGate;
import net.linlan.dsh.domain.tool.service.ToolArgumentsParser;
import net.linlan.dsh.domain.tool.service.ToolCallExecutor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class AgentRunFactory implements AgentRunLifecycle {

    private final ILlmRuntimePort llm;
    private final ToolRegistry sharedToolRegistry;
    private final SystemPromptAssembler systemPromptAssembler;
    private final UserQuestionGateway userQuestions;
    private final AgentToolCatalog toolCatalog;
    private final ToolArgumentsParser toolArgumentsParser;
    private final IHookService hookService;
    private final IPermissionMatrixPort permissionMatrixPort;
    private final RuntimeApprovalGateway approvalGateway;
    private final SessionLogFactory sessionLogFactory;
    private final AgentEventListenerFactory agentEventListenerFactory;
    private final CompactionEngine compactionEngine;
    private final long toolTimeoutMs;
    private final ConcurrentMap<String, AgentRun> liveAgents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MatrixRuntimeApprovalGate> approvalGates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicReference<String>> cwdRefs = new ConcurrentHashMap<>();

    public AgentRunFactory(
            ILlmRuntimePort llm,
            ToolRegistry sharedToolRegistry,
            SystemPromptAssembler systemPromptAssembler,
            UserQuestionGateway userQuestions,
            AgentToolCatalog toolCatalog,
            ToolArgumentsParser toolArgumentsParser,
            IHookService hookService,
            IPermissionMatrixPort permissionMatrixPort,
            RuntimeApprovalGateway approvalGateway,
            SessionLogFactory sessionLogFactory,
            AgentEventListenerFactory agentEventListenerFactory,
            CompactionEngine compactionEngine,
            long toolTimeoutMs
    ) {
        this.llm = llm;
        this.sharedToolRegistry = sharedToolRegistry;
        this.systemPromptAssembler = systemPromptAssembler;
        this.userQuestions = userQuestions;
        this.toolCatalog = toolCatalog;
        this.toolArgumentsParser = toolArgumentsParser;
        this.hookService = hookService;
        this.permissionMatrixPort = permissionMatrixPort;
        this.approvalGateway = approvalGateway;
        this.sessionLogFactory = sessionLogFactory;
        this.agentEventListenerFactory = agentEventListenerFactory;
        this.compactionEngine = compactionEngine;
        this.toolTimeoutMs = toolTimeoutMs;
    }

    @Override
    public AgentRun create(String agentId, AgentOptions options, String cwd) {
        String sessionId = UUID.randomUUID().toString();
        SessionHeader header = SessionHeader.create(sessionId, Instant.now(), cwd, agentId);
        SessionLog session = sessionLogFactory.create(header);
        return wireAgent(agentId, options, cwd, session);
    }

    @Override
    public AgentRun resume(String agentId, AgentOptions options, String cwd, SessionLog session) {
        return wireAgent(agentId, options, cwd, session);
    }

    @Override
    public AgentRun get(String agentId) {
        return liveAgents.get(agentId);
    }

    @Override
    public void configureApprovalMode(String agentId, ApprovalModeVO mode) {
        MatrixRuntimeApprovalGate gate = approvalGates.get(agentId);
        if (gate != null) {
            gate.setApprovalMode(mode);
        }
    }

    @Override
    public void updateCwd(String agentId, String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return;
        }
        AtomicReference<String> reference = cwdRefs.get(agentId);
        if (reference != null) {
            reference.set(cwd);
        }
    }

    public java.util.Collection<AgentRun> all() {
        return liveAgents.values();
    }

    @Override
    public CompletableFuture<Void> disposeAll() {
        return CompletableFuture.allOf(
                liveAgents.values().stream()
                        .map(AgentRun::dispose)
                        .toArray(CompletableFuture[]::new)
        );
    }

    private AgentRun wireAgent(String agentId, AgentOptions options, String cwd, SessionLog session) {
        Inbox inbox = new Inbox(session, LoggingInboxNotifications.INSTANCE);
        String sessionId = session.sessionId();
        AtomicReference<AgentOptions> optionsReference = new AtomicReference<>(options);
        AtomicReference<String> cwdReference = new AtomicReference<>(cwd);
        cwdRefs.put(agentId, cwdReference);
        ToolRegistry toolRegistry = toolCatalog.createRegistry(
                new AgentToolContext(agentId, sessionId, cwd, session, userQuestions, optionsReference::get, cwdReference),
                sharedToolRegistry
        );

        ToolCallExecutor executor = new ToolCallExecutor(
                toolRegistry,
                session,
                10,
                toolArgumentsParser,
                toolTimeoutMs
        );
        if (hookService != null) {
            executor.setHookService(hookService);
        }
        if (permissionMatrixPort != null && approvalGateway != null) {
            MatrixRuntimeApprovalGate gate = new MatrixRuntimeApprovalGate(
                    permissionMatrixPort,
                    agentId,
                    (request, ignored) -> approvalGateway.requestApproval(request)
            );
            gate.setApprovalMode(options.approvalMode());
            executor.setApprovalGate(gate);
            approvalGates.put(agentId, gate);
        }

        AgentEventListener agentEventListener = agentEventListenerFactory == null
                ? null
                : agentEventListenerFactory.create(session.sessionId());

        ReactLoopAgent agent = new ReactLoopAgent(
                agentId,
                options,
                cwd,
                session,
                inbox,
                llm,
                toolRegistry,
                executor,
                systemPromptAssembler,
                agentEventListener,
                compactionEngine,
                optionsReference::set
        );
        liveAgents.put(agentId, agent);
        return agent;
    }

    @FunctionalInterface
    public interface SessionLogFactory {
        SessionLog create(SessionHeader header);
    }

    @FunctionalInterface
    public interface AgentEventListenerFactory {
        AgentEventListener create(String sessionId);
    }
}
