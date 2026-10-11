package net.linlan.dsh.domain.agent.service.run.tool;

import net.linlan.dsh.domain.agent.contract.outbound.UserQuestionGateway;
import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.service.ask.AskUserTool;
import net.linlan.dsh.domain.agent.service.subagent.SubagentRegistry;
import net.linlan.dsh.domain.agent.service.subagent.SubagentTool;
import net.linlan.dsh.domain.goal.IGoalCommandService;
import net.linlan.dsh.domain.goal.IGoalQueryService;
import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.lsp.ILspService;
import net.linlan.dsh.domain.plan.ExitPlanModeTool;
import net.linlan.dsh.domain.plan.model.valobj.PlanModeState;
import net.linlan.dsh.domain.plan.service.PlanModeService;
import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.IConversationQueryService;
import net.linlan.dsh.domain.todo.service.TodoWriteService;
import net.linlan.dsh.domain.todo.service.TodoWriteTool;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.fs.FsEditTool;
import net.linlan.dsh.domain.tool.fs.FsGrepTool;
import net.linlan.dsh.domain.tool.fs.FsReadImageTool;
import net.linlan.dsh.domain.tool.fs.FsReadTool;
import net.linlan.dsh.domain.tool.fs.FsSearchTool;
import net.linlan.dsh.domain.tool.fs.FsWriteTool;
import net.linlan.dsh.domain.tool.fs.StrReplaceEditorTool;
import net.linlan.dsh.domain.tool.fs.CwdResolvingFsPort;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.tool.goal.GoalTools;
import net.linlan.dsh.domain.tool.jobs.JobKillTool;
import net.linlan.dsh.domain.tool.jobs.JobListTool;
import net.linlan.dsh.domain.tool.jobs.JobOutputTool;
import net.linlan.dsh.domain.tool.jobs.JobRunTool;
import net.linlan.dsh.domain.tool.lsp.LspTool;
import net.linlan.dsh.domain.tool.schedule.ScheduleTools;
import net.linlan.dsh.domain.tool.session.SessionSearchTool;
import net.linlan.dsh.domain.tool.shell.IShellExecutor;
import net.linlan.dsh.domain.tool.shell.ShellExecuteTool;
import net.linlan.dsh.domain.tool.plugin.PluginStatusTool;
import net.linlan.dsh.domain.tool.terminal.TerminalTools;
import net.linlan.dsh.domain.tool.web.WebFetchTool;
import net.linlan.dsh.domain.tool.web.WebSearchTool;
import net.linlan.dsh.domain.tool.web.adapter.port.IWebPort;
import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.tool.skill.SkillTool;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class AgentToolCatalog {

    private final IShellExecutor shellExecutor;
    private final IFsPort fsPort;
    private final IShellExecutor unrestrictedShellExecutor;
    private final IFsPort unrestrictedFsPort;
    private final IWebPort webPort;
    private final PlanModeService planModeService;
    private final TodoWriteService todoWriteService;
    private final SubagentRegistry subagentRegistry;
    private final ISkillService skillService;
    private final IJobRegistryService jobRegistryService;
    private final IGoalQueryService goalQueryService;
    private final IGoalCommandService goalCommandService;
    private final IScheduleService scheduleService;
    private final ILspService lspService;
    private final IConversationQueryService conversationQueryService;
    private final net.linlan.dsh.domain.terminal.adapter.port.ITerminalPort terminalPort;
    private final IHarnessPluginRepository pluginRepository;
    private final IPluginRuntimeService pluginRuntimeService;

    public AgentToolCatalog(
            IShellExecutor shellExecutor,
            IFsPort fsPort,
            IShellExecutor unrestrictedShellExecutor,
            IFsPort unrestrictedFsPort,
            IWebPort webPort,
            PlanModeService planModeService,
            TodoWriteService todoWriteService,
            SubagentRegistry subagentRegistry,
            ISkillService skillService,
            IJobRegistryService jobRegistryService,
            IGoalQueryService goalQueryService,
            IGoalCommandService goalCommandService,
            IScheduleService scheduleService,
            ILspService lspService,
            IConversationQueryService conversationQueryService,
            net.linlan.dsh.domain.terminal.adapter.port.ITerminalPort terminalPort,
            IHarnessPluginRepository pluginRepository,
            IPluginRuntimeService pluginRuntimeService
    ) {
        this.shellExecutor = shellExecutor;
        this.fsPort = fsPort;
        this.unrestrictedShellExecutor = unrestrictedShellExecutor;
        this.unrestrictedFsPort = unrestrictedFsPort;
        this.webPort = webPort;
        this.planModeService = planModeService;
        this.todoWriteService = todoWriteService;
        this.subagentRegistry = subagentRegistry;
        this.skillService = skillService;
        this.jobRegistryService = jobRegistryService;
        this.goalQueryService = goalQueryService;
        this.goalCommandService = goalCommandService;
        this.scheduleService = scheduleService;
        this.lspService = lspService;
        this.conversationQueryService = conversationQueryService;
        this.terminalPort = terminalPort;
        this.pluginRepository = pluginRepository;
        this.pluginRuntimeService = pluginRuntimeService;
    }

    public ToolRegistry createRegistry(AgentToolContext context, ToolRegistry sharedRegistry) {
        InMemoryToolRegistry localRegistry = new InMemoryToolRegistry();
        AtomicReference<PlanModeState> planState =
                new AtomicReference<>(new PlanModeState.Off());
        Supplier<AgentOptions> optionsSupplier = context.optionsSupplier();
        IShellExecutor effectiveShellExecutor = unrestrictedShellExecutor == null || optionsSupplier == null
                ? shellExecutor
                : new ApprovalAwareShellExecutor(shellExecutor, unrestrictedShellExecutor, optionsSupplier);
        IFsPort effectiveFsPort = unrestrictedFsPort == null || optionsSupplier == null
                ? fsPort
                : new ApprovalAwareFsPort(fsPort, unrestrictedFsPort, optionsSupplier);
        CwdResolvingFsPort agentFsPort = new CwdResolvingFsPort(
                effectiveFsPort,
                context.cwdRef() != null ? context.cwdRef() : new AtomicReference<>(context.cwd()));
        Supplier<String> cwdSupplier = context.cwdRef() != null ? context.cwdRef()::get : () -> context.cwd();

        localRegistry.register(new TodoWriteTool(context.session(), false, todoWriteService));
        localRegistry.register(new AskUserTool(context.userQuestions(), context.agentId()));
        localRegistry.register(new ExitPlanModeTool(context.session(), planState, planModeService));
        localRegistry.register(new ShellExecuteTool(effectiveShellExecutor, context.cwd()));
        localRegistry.register(new FsReadTool(agentFsPort));
        localRegistry.register(new FsWriteTool(agentFsPort, cwdSupplier));
        localRegistry.register(new FsSearchTool(agentFsPort));
        localRegistry.register(new StrReplaceEditorTool(agentFsPort, cwdSupplier));
        localRegistry.register(new FsGrepTool(agentFsPort));
        localRegistry.register(new FsReadImageTool(agentFsPort));
        localRegistry.register(new FsEditTool(agentFsPort, cwdSupplier));
        localRegistry.register(new PluginStatusTool(pluginRepository, pluginRuntimeService));
        localRegistry.register(new WebSearchTool(webPort));
        localRegistry.register(new WebFetchTool(webPort));
        if (lspService != null) {
            localRegistry.register(new LspTool(lspService, agentFsPort));
        }
        if (terminalPort != null) {
            localRegistry.register(new TerminalTools.Open(terminalPort));
            localRegistry.register(new TerminalTools.Send(terminalPort));
            localRegistry.register(new TerminalTools.Read(terminalPort));
            localRegistry.register(new TerminalTools.Close(terminalPort));
            localRegistry.register(new TerminalTools.ListSessions(terminalPort));
        }
        if (jobRegistryService != null) {
            localRegistry.register(new JobRunTool(jobRegistryService, shellExecutor, context.sessionId(), context.cwd()));
            localRegistry.register(new JobListTool(jobRegistryService, context.sessionId()));
            localRegistry.register(new JobOutputTool(jobRegistryService, context.sessionId()));
            localRegistry.register(new JobKillTool(jobRegistryService, context.sessionId()));
        }
        if (context.session() != null) {
            if (goalQueryService != null) {
                localRegistry.register(new GoalTools.Get(goalQueryService, context.sessionId()));
            }
            if (goalCommandService != null) {
                localRegistry.register(new GoalTools.Create(goalCommandService, context.sessionId()));
                localRegistry.register(new GoalTools.Update(goalCommandService, context.sessionId()));
            }
            if (scheduleService != null) {
                localRegistry.register(new ScheduleTools.Create(scheduleService, context.sessionId()));
                localRegistry.register(new ScheduleTools.ListAll(scheduleService, context.sessionId()));
                localRegistry.register(new ScheduleTools.Delete(scheduleService, context.sessionId()));
            }
        }
        if (conversationQueryService != null) {
            localRegistry.register(new SessionSearchTool(conversationQueryService));
        }
        if (skillService != null) {
            localRegistry.register(new SkillTool(skillService, context.cwd()));
        }

        if (subagentRegistry != null && !subagentRegistry.listProviderNames().isEmpty()) {
            String providerName = subagentRegistry.listProviderNames().get(0);
            localRegistry.register(new SubagentTool(
                    subagentRegistry, providerName, "subagent", context.optionsSupplier()));
        }

        return new CompositeToolRegistry(localRegistry, sharedRegistry);
    }

    public record AgentToolContext(
            String agentId,
            String sessionId,
            String cwd,
            SessionLog session,
            UserQuestionGateway userQuestions,
            Supplier<AgentOptions> optionsSupplier,
            AtomicReference<String> cwdRef
    ) {
        public AgentToolContext(
                String agentId,
                String sessionId,
                String cwd,
                SessionLog session,
                UserQuestionGateway userQuestions,
                Supplier<AgentOptions> optionsSupplier
        ) {
            this(agentId, sessionId, cwd, session, userQuestions, optionsSupplier, null);
        }

        public AgentToolContext(
                String agentId,
                String sessionId,
                String cwd,
                SessionLog session,
                UserQuestionGateway userQuestions
        ) {
            this(agentId, sessionId, cwd, session, userQuestions, () -> null, null);
        }
    }
}
