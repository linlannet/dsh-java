package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.domain.agent.contract.outbound.UserQuestionGateway;
import net.linlan.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import net.linlan.dsh.domain.agent.contract.outbound.RuntimeApprovalGateway;
import net.linlan.dsh.domain.agent.service.run.AgentRunFactory;
import net.linlan.dsh.domain.agent.service.run.InMemoryUserQuestionGateway;
import net.linlan.dsh.infrastructure.adapter.agent.PersistingAgentEventListener;
import net.linlan.dsh.domain.agent.service.run.PersistingSessionLog;
import net.linlan.dsh.domain.agent.service.run.RuntimeApprovalBroker;
import net.linlan.dsh.domain.agent.service.run.tool.InMemoryToolRegistry;
import net.linlan.dsh.domain.agent.service.run.tool.AgentToolCatalog;
import net.linlan.dsh.infrastructure.adapter.tool.JacksonToolArgumentsParser;
import net.linlan.dsh.infrastructure.adapter.fs.LocalFsService;
import net.linlan.dsh.infrastructure.adapter.shell.LocalShellExecutor;
import net.linlan.dsh.infrastructure.adapter.subagent.AcpSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.ClaudeCodeSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.CodexSubagentProvider;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanDao;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanEndpointDao;
import net.linlan.dsh.infrastructure.digitalhuman.AggregatingRuntimeApprovalGateway;
import net.linlan.dsh.infrastructure.digitalhuman.RemoteEndpointCredentialRegistry;
import net.linlan.dsh.domain.agent.service.SystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.subagent.SubagentRegistry;
import net.linlan.dsh.domain.agent.service.subagent.SpawnInProcessProvider;
import net.linlan.dsh.domain.runtime.setting.service.AgentModelSettingResolver;
import net.linlan.dsh.domain.goal.IGoalCommandService;
import net.linlan.dsh.domain.goal.IGoalQueryService;
import net.linlan.dsh.domain.jobs.IJobRegistryService;
import net.linlan.dsh.domain.plugin.IPluginRuntimeService;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.lsp.ILspService;
import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.session.IConversationQueryService;
import net.linlan.dsh.domain.hooks.IHookService;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.service.LlmGenerationService;
import net.linlan.dsh.domain.agent.service.compaction.BasicCompactionEngine;
import net.linlan.dsh.domain.agent.service.compaction.CompactionEngine;
import net.linlan.dsh.domain.plan.service.PlanModeService;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionEventStore;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.task.permission.adapter.port.IPermissionMatrixPort;
import net.linlan.dsh.domain.todo.service.TodoWriteService;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.service.ToolArgumentsParser;
import net.linlan.dsh.domain.tool.shell.IShellExecutor;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.tool.web.adapter.port.IWebPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import java.util.function.Function;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AgentRun 运行时组合根。
 * <p>负责把领域核心、外部能力端口和运行期治理策略组装成可执行的 Agent。</p>
 */
@Configuration
public class AgentRunBeanConfig {

    @Bean
    public LlmGenerationService llmGenerationService(ILlmRuntimePort llmRuntimePort) {
        return new LlmGenerationService(llmRuntimePort);
    }

    @Bean
    public CompactionEngine compactionEngine(
            LlmGenerationService llmGenerationService,
            @Value("${harness.agent.compaction.pressure-threshold-tokens:100000}") long pressureThresholdTokens,
            @Value("${harness.agent.compaction.min-messages-to-compact:6}") int minMessagesToCompact
    ) {
        return new BasicCompactionEngine(llmGenerationService, pressureThresholdTokens, minMessagesToCompact);
    }

    @Bean
    public SubagentRegistry subagentRegistry() {
        return new SubagentRegistry();
    }

    @Bean
    public ToolRegistry toolRegistry() {
        return new InMemoryToolRegistry();
    }

    @Bean
    public ToolArgumentsParser toolArgumentsParser(ObjectMapper objectMapper) {
        return new JacksonToolArgumentsParser(objectMapper);
    }

    @Bean
    public SystemPromptAssembler systemPromptAssembler() {
        String identity = "You are DeepSeek Harness, an intelligent AI coding and task assistant.";
        String persona = """
                ## 角色
                你是一名专业、高效的 AI 助手，擅长编程、技术问答、文件操作和任务执行。
                默认使用中文回答，除非用户使用英文提问。

                ## 回答规范
                - 代码块必须标注语言（```java、```python 等）。
                - 默认回答 2-5 句，先给结论再展开细节；不要只用一句话草草收尾。
                - 复杂任务分步骤执行，每步说明意图，必要时给出下一步建议。
                - 不确定时主动澄清，不要猜测。

                ## 工具使用（重要）
                - 你处在一个可以调用真实工具的运行环境中。工具以 function calling 形式提供，必须真正发起工具调用才能获得结果。
                - 当任务涉及「读取/列出/搜索/写入文件、执行命令、查看目录、访问网页、查询代码」等任何需要真实数据的操作时，你必须调用对应工具，严禁凭空编造或复述请求。
                - **扩展管理**：用户要求安装/启用/停用技能，或添加/修改/删除 MCP 服务时，直接使用 extension_skill_install（从 Git 仓库拉取安装技能）、extension_skill_list、extension_skill_manage、extension_mcp_list、extension_mcp_configure、extension_mcp_remove 工具完成，不要只给出口头指引。安装技能时若用户未给出仓库地址，可先通过 web 搜索找到合适的技能仓库再安装。
                - 即使用户只说一句"列出当前目录的文件"，也必须调用工具获取真实结果，而不是回答"我来列出"。
                - **列出目录内容（一级子目录/文件）时，优先用 `shell_execute` 执行 `ls -la <目录>` 或 `find <目录> -maxdepth 1 -type d`；`fs_search` 是 glob 文件搜索工具，不适合列目录。**
                - 使用 `shell_execute` 时，优先通过 `cwd` 参数指定工作目录；不要执行 `cd ~/...`、`cd /` 或 `cd $HOME`，工作区写模式下这些命令会被沙箱拒绝。
                - 工具调用前简要说明目的，调用后基于真实结果解读。
                - 危险操作（删除、覆盖）前确认。
                - 任务完成后停止调用工具，给出最终回复。
                - 除非用户明确要求禁用工具，否则根据任务需要自主选择工具。
                - 工具失败时不要编造结果：说明失败原因，尝试更安全的替代路径或请求澄清。

                ## 网页与图表产物
                - 生成 HTML、ECharts 或可视化页面后，只需写入文件并在回复中给出绝对路径或 Markdown 文件链接。
                - 不要调用系统 `open`、`open_external`、`xdg-open` 或浏览器命令打开产物；桌面端会在 DSH Java Desktop 右侧面板内嵌预览。
                - ECharts 页面应优先使用内联脚本和可访问的资源链接，确保在桌面端内嵌 WebView 中可运行。
                - **draw.io 图表**：用户要求流程图/架构图/泳道图/ER 图等，或要求按 draw.io 插件交付时，生成 `.drawio` 文件（内容为 `<mxfile><diagram><mxGraphModel>…</mxGraphModel></diagram></mxfile>` 包裹的合法 XML）：节点用 `vertex="1"` 的 `mxCell` 描述（`style` 控制圆角/填充/字体，`mxGeometry` 定位），连线用 `edge="1"` 的 `mxCell` 并以 `source`/`target` 关联节点；合理布局避免重叠，写入文件后在回复中给出绝对路径，桌面端会内嵌渲染并支持编辑。

                ## 工具结果解读（严格遵循）
                - 工具返回的就是真实数据，直接原样引用，不要二次加工或"翻译"。
                - 列出文件/目录时，用简洁的 Markdown 列表或代码块展示完整结果，不要截断、不要省略、不要说"还有很多"。
                - 如果工具结果很长，先总结关键信息，再用折叠块（```text ... ```）附完整结果。
                - 严禁出现"项目内部文件占满""无法完整体现"这类模糊表述——工具返回了什么就展示什么。
                - 最终回复中不要包含反引号不匹配的片段、乱码或未完成的句子。
                - 每次回复必须是完整、自洽的一段话，检查无误后再输出。

                ## 格式
                - 使用 Markdown 格式化输出。
                - 长回答用标题和列表组织结构。
                - 不要在正文中输出内部推理过程或 <think> 之类的标签，只输出最终回答。
                """;
        var assembler = new ScopedSystemPromptAssembler(identity, persona, List.of());
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext(
                "harness:runtime",
                20,
                (Function<ScopedSystemPromptAssembler.AssembleContext, String>) context -> "## Runtime context\n- Working directory: "
                        + (context.cwd() == null || context.cwd().isBlank()
                                ? System.getProperty("user.dir") : context.cwd())
        ));
        return assembler;
    }

    @Bean
    public InMemoryUserQuestionGateway userQuestionsGateway() {
        return new InMemoryUserQuestionGateway();
    }

    @Bean
    public RuntimeApprovalBroker runtimeApprovalBroker(
            @Value("${harness.approval.runtime-timeout-ms:600000}") long timeoutMs
    ) {
        return new RuntimeApprovalBroker(timeoutMs);
    }

    @Bean
    @Primary
    public RuntimeApprovalGateway runtimeApprovalGateway(
            RuntimeApprovalBroker runtimeApprovalBroker,
            IDigitalHumanEndpointDao endpointDao,
            IDigitalHumanDao humanDao,
            RemoteEndpointCredentialRegistry remoteCredentials
    ) {
        return new AggregatingRuntimeApprovalGateway(runtimeApprovalBroker, endpointDao, humanDao, remoteCredentials);
    }

    @Bean
    public Object agentRunFactorySubagentInit(
            SubagentRegistry subagentRegistry,
            AgentRunLifecycle agentRunFactory,
            AgentModelSettingResolver modelSettingResolver,
            ObjectProvider<ClaudeCodeSubagentProvider> claudeProvider,
            ObjectProvider<CodexSubagentProvider> codexProvider,
            ObjectProvider<AcpSubagentProvider> acpProvider
    ) {
        var spawn = new SpawnInProcessProvider(
                "spawn-in-process",
                subagentRegistry,
                agentRunFactory,
                () -> modelSettingResolver.resolve(null, 4096)
        );
        subagentRegistry.register(spawn);
        ClaudeCodeSubagentProvider claude = claudeProvider.getIfAvailable();
        if (claude != null) {
            subagentRegistry.register(claude);
        }
        CodexSubagentProvider codex = codexProvider.getIfAvailable();
        if (codex != null) {
            subagentRegistry.register(codex);
        }
        AcpSubagentProvider acp = acpProvider.getIfAvailable();
        if (acp != null) {
            subagentRegistry.register(acp);
        }
        return spawn;
    }

    @Bean
    public AgentRunFactory agentRunFactory(
            ILlmRuntimePort llmRuntimePort,
            ToolRegistry toolRegistry,
            SystemPromptAssembler systemPromptAssembler,
            UserQuestionGateway userQuestions,
            IHookService hookService,
            ToolArgumentsParser toolArgumentsParser,
            IPermissionMatrixPort permissionMatrixPort,
            RuntimeApprovalGateway runtimeApprovalBroker,
            SubagentRegistry subagentRegistry,
            IShellExecutor shellExecutor,
            IFsPort fsService,
            @Value("${harness.agent.default-cwd:#{systemProperties['user.dir']}}") String defaultCwd,
            IWebPort webService,
            IJobRegistryService jobRegistryService,
            IGoalQueryService goalQueryService,
            IGoalCommandService goalCommandService,
            IScheduleService scheduleService,
            ILspService lspService,
            IConversationQueryService conversationQueryService,
            net.linlan.dsh.domain.terminal.adapter.port.ITerminalPort terminalPort,
            PlanModeService planModeService,
            TodoWriteService todoWriteService,
            net.linlan.dsh.domain.skill.ISkillService skillService,
            ObjectMapper objectMapper,
            ObjectProvider<ISessionEventStore> sessionEventStoreProvider,
            CompactionEngine compactionEngine,
            IHarnessPluginRepository pluginRepository,
            IPluginRuntimeService pluginRuntimeService,
            @Value("${harness.agent.tool-timeout-ms:300000}") long toolTimeoutMs
    ) {
        ISessionEventStore sessionEventStore = sessionEventStoreProvider.getIfAvailable();
        AgentRunFactory.SessionLogFactory sessionLogFactory = header -> sessionEventStore == null
                ? new SessionLog(header.sessionId(), header)
                : new PersistingSessionLog(header.sessionId(), header, sessionEventStore);
        AgentRunFactory.AgentEventListenerFactory agentEventListenerFactory = sessionEventStore == null
                ? null
                : sessionId -> new PersistingAgentEventListener(sessionId, sessionEventStore, objectMapper);

        AgentToolCatalog toolCatalog = new AgentToolCatalog(
                shellExecutor,
                fsService,
                new LocalShellExecutor(),
                new LocalFsService(defaultCwd, false),
                webService,
                planModeService,
                todoWriteService,
                subagentRegistry,
                skillService,
                jobRegistryService,
                goalQueryService,
                goalCommandService,
                scheduleService,
                lspService,
                conversationQueryService,
                terminalPort,
                pluginRepository,
                pluginRuntimeService
        );

        return new AgentRunFactory(
                llmRuntimePort,
                toolRegistry,
                systemPromptAssembler,
                userQuestions,
                toolCatalog,
                toolArgumentsParser,
                hookService,
                permissionMatrixPort,
                runtimeApprovalBroker,
                sessionLogFactory,
                agentEventListenerFactory,
                compactionEngine,
                toolTimeoutMs
        );
    }
}
