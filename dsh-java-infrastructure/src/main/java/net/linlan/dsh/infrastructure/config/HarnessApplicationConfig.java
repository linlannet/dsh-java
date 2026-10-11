package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPreset;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPresets;
import net.linlan.dsh.domain.terminal.adapter.port.ITerminalPort;
import net.linlan.dsh.domain.sandbox.ISandboxPolicyService;
import net.linlan.dsh.domain.sandbox.service.SandboxExtraRootsRegistry;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.tool.shell.IShellExecutor;
import net.linlan.dsh.domain.tool.web.adapter.port.IWebPort;
import net.linlan.dsh.infrastructure.adapter.fs.LocalFsService;
import net.linlan.dsh.infrastructure.adapter.fs.SandboxedFsService;
import net.linlan.dsh.infrastructure.adapter.llm.InMemoryLlmRuntimePort;
import net.linlan.dsh.infrastructure.adapter.llm.LlmAdapterFactory;
import net.linlan.dsh.infrastructure.adapter.llm.openai.OpenAiCompatibleAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DirectSeekCatalogModel;
import net.linlan.dsh.infrastructure.adapter.sandbox.SandboxedShellExecutor;
import net.linlan.dsh.infrastructure.adapter.shell.LocalShellExecutor;
import net.linlan.dsh.infrastructure.adapter.web.LocalWebService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.dsh.domain.typert.adapter.port.ITypertRegistryContract;
import net.linlan.dsh.infrastructure.adapter.typert.InMemoryTypertRegistry;
import net.linlan.dsh.infrastructure.adapter.typert.TypertProtocolLoader;

import net.linlan.dsh.infrastructure.adapter.acp.StubAcpAgentPort;

import net.linlan.dsh.domain.workflow.adapter.port.IWorkflowEnginePort;
import net.linlan.dsh.infrastructure.adapter.workflow.LocalWorkflowEnginePort;

import net.linlan.dsh.domain.acp.adapter.port.IAcpAgentPort;
import net.linlan.dsh.domain.coderuntime.adapter.port.ICodeRuntimePort;
import net.linlan.dsh.infrastructure.adapter.acp.StdioAcpAgentPort;
import net.linlan.dsh.infrastructure.adapter.coderuntime.LocalCodeRuntimePort;
import net.linlan.dsh.infrastructure.adapter.subagent.AcpSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.ClaudeCodeSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.CodexSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.terminal.LocalTerminalPort;

import net.linlan.dsh.domain.skill.adapter.port.ISkillProviderPort;
import net.linlan.dsh.infrastructure.adapter.skill.FilesystemSkillProviderPort;

import net.linlan.dsh.domain.hooks.adapter.port.IHookRunnerPort;
import net.linlan.dsh.infrastructure.adapter.hooks.LocalShellHookRunner;

import net.linlan.dsh.domain.jobs.adapter.repository.IJobRegistryRepository;
import net.linlan.dsh.infrastructure.adapter.jobs.InMemoryJobRegistryRepository;

import net.linlan.dsh.domain.schedule.adapter.repository.IScheduleRepository;
import net.linlan.dsh.infrastructure.adapter.schedule.InMemoryScheduleRepository;


import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.infrastructure.adapter.storage.JsonStorageBackendPort;
import net.linlan.dsh.domain.tool.plugin.IPluginToolBridge;
import net.linlan.dsh.infrastructure.adapter.plugin.JsonRpcPluginToolBridge;
import java.nio.file.Path;

import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.session.event.adapter.repository.ISessionProjectionCache;
import net.linlan.dsh.infrastructure.adapter.credentials.LocalCredentialProviderPort;
import net.linlan.dsh.domain.session.event.service.InMemorySessionProjectionCache;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 基础设施装配配置。
 * <p>
 * 执行流程：
 * 1. 将领域层接口与基础设施实现绑定；
 * 2. 按功能域装配各类 Bean；
 * 3. 为触发层和用例层提供统一注入入口。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Configuration
@EnableConfigurationProperties(HarnessExtensionsProperties.class)
public class HarnessApplicationConfig {

    @Bean
    public IPluginToolBridge jsonRpcPluginToolBridge() {
        return new JsonRpcPluginToolBridge();
    }

    // ── 大模型层 Bean ───────────────────────────────────────
    /**
     * llm Runtime Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param adapterList adapter List参数。
     * @return 方法执行结果。
     */


    @Bean
    public ILlmRuntimePort llmRuntimePort(List<LlmAdapter> adapterList, LlmAdapterFactory llmAdapterFactory) {
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        for (LlmAdapter adapter : adapterList) {
            String providerId = adapter.providerId();
            runtime.registerAdapter(providerId, adapter);
        }
        // 按渠道模板注册表补齐协议路由适配器：任何模板 provider（qwen/zhipu/doubao/anthropic/ollama…）
        // 与 custom 都无需单独写 Bean，运行时按模型设置中的 protocol 动态分派。
        for (ChannelPreset preset : ChannelPresets.all()) {
            if (!runtime.hasAdapter(preset.id())) {
                runtime.registerAdapter(preset.id(), llmAdapterFactory.create(preset.id()));
            }
        }
        // 兜底：用户在控制台填写了模板之外的 providerCode 时，按其 protocol 动态路由，
        // 不再需要为每个新渠道注册 Bean（对应 WaLiAPI get_adaptor 的 default 分支）。
        runtime.setFallbackFactory(llmAdapterFactory::create);
        return runtime;
    }

    @Bean
    public DeepSeekAdapter deepSeekAdapter(
            ObjectMapper objectMapper,
            @Value("${harness.llm.deepseek.base-url:${LLM_BASE_URL:${DEEPSEEK_BASE_URL:http://127.0.0.1:28080/v1}}}") String baseUrl,
            @Value("${harness.llm.deepseek.api-key:${LLM_API_KEY:${DEEPSEEK_API_KEY:sk-local}}}") String apiKey,
            @Value("${harness.llm.deepseek.max-tokens:8192}") int maxTokens,
            @Value("${harness.llm.deepseek.context-window:128000}") int contextWindow,
            @Value("${harness.llm.deepseek.default-model:${LLM_DEFAULT_MODEL:${DEEPSEEK_DEFAULT_MODEL:deepseek}}}") String defaultModel
    ) {
        var models = List.of(
                new DirectSeekCatalogModel(defaultModel, defaultModel, null, contextWindow, maxTokens)
        );
        ResolvedRetryPolicy retryPolicy = new ResolvedRetryPolicy.Normal(
                500, 10_000, 0.1, 2,
                LlmErrorCodes.DEFAULT_RETRYABLE_CODES
        );
        var options = new DeepSeekAdapterOptions(baseUrl, apiKey, maxTokens, contextWindow,
                DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS, models, retryPolicy);
        return new DeepSeekAdapter(options, objectMapper);
    }
    /**
     * openai Adapter。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */


    @Bean
    public OpenAiCompatibleAdapter openaiAdapter(ObjectMapper objectMapper) {
        var options = new DeepSeekAdapterOptions(
                "https://api.openai.com/v1",
                System.getenv("OPENAI_API_KEY") != null ? System.getenv("OPENAI_API_KEY") : "",
                4096, 128000, DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                List.of(), null);
        return new OpenAiCompatibleAdapter("openai", options, objectMapper);
    }
    /**
     * ollama Adapter。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */


    @Bean
    public OpenAiCompatibleAdapter ollamaAdapter(ObjectMapper objectMapper) {
        var options = new DeepSeekAdapterOptions(
                "http://127.0.0.1:11434/v1",
                "ollama",
                4096, 128000, DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                List.of(), null);
        return new OpenAiCompatibleAdapter("ollama", options, objectMapper);
    }

    // ── 第二阶段：Shell + 文件系统基础设施 Bean ────────────────────

    @Bean
    public IShellExecutor shellExecutor(
            ISandboxPolicyService sandboxPolicyService,
            @Value("${harness.agent.default-cwd:#{systemProperties['user.dir']}}") String defaultCwd,
            @Value("${harness.sandbox.shell-enabled:true}") boolean sandboxEnabled,
            SandboxExtraRootsRegistry sandboxExtraRootsRegistry
    ) {
        IShellExecutor raw = new LocalShellExecutor();
        // 沙箱开关：默认启用，可通过 harness.sandbox.shell-enabled=false 回退裸执行
        return sandboxEnabled
                ? new SandboxedShellExecutor(raw, sandboxPolicyService, defaultCwd, () -> sandboxExtraRootsRegistry.current())
                : raw;
    }

    @Bean
    public IFsPort fsService(
            @Value("${harness.agent.default-cwd:#{systemProperties['user.dir']}}") String defaultCwd,
            ISandboxPolicyService sandboxPolicyService,
            @Value("${harness.sandbox.fs-enabled:true}") boolean sandboxEnabled,
            SandboxExtraRootsRegistry sandboxExtraRootsRegistry
    ) {
        return sandboxEnabled
                ? new SandboxedFsService(defaultCwd, sandboxPolicyService, () -> sandboxExtraRootsRegistry.current())
                : new LocalFsService(defaultCwd);
    }

    @Bean
    public IWebPort webService(
            @Value("${harness.web.search-api-base:}") String searchApiBase,
            @Value("${harness.web.search-api-key:}") String searchApiKey
    ) {
        if (searchApiBase != null && !searchApiBase.isBlank() && searchApiKey != null && !searchApiKey.isBlank()) {
            return new LocalWebService(searchApiBase, searchApiKey);
        }
        return new LocalWebService();
    }

    // ── 凭据层 ────────────────────────────────────────

    @Bean
    public ICredentialProviderPort credentialProviderPort(
            @Value("${harness.credentials.home:${user.home}/.dsh}") String dshHome
    ) {
        LocalCredentialProviderPort port = new LocalCredentialProviderPort(Path.of(dshHome));
        port.init();
        return port;
    }

    // ── 存储层 ────────────────────────────────────────────

    @Bean
    public IStorageBackendPort storageBackendPort(
            ObjectMapper objectMapper,
            @Value("${harness.storage.root:#{systemProperties['user.home']}/.dsh/storage}") String storageRoot
    ) {
        return new JsonStorageBackendPort(Path.of(storageRoot), objectMapper);
    }

    // ── 守卫层 ───────────────────────────────────────────────

    @Bean
    public ITerminalPort terminalPort(
            @Value("${harness.terminal.default-cwd:#{systemProperties['user.dir']}}") String defaultCwd
    ) {
        return new LocalTerminalPort(defaultCwd);
    }

    // ── 调度层 ───────────────────────────────────────────
    /**
     * schedule Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Bean
    public IScheduleRepository scheduleRepository() {
        return new InMemoryScheduleRepository();
    }

    // ── 任务层 ───────────────────────────────────────────────
    /**
     * job Registry Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Bean
    public IJobRegistryRepository jobRegistryRepository() {
        return new InMemoryJobRegistryRepository();
    }

    // ── 钩子层 ──────────────────────────────────────────────
    /**
     * hook Runner Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Bean
    public IHookRunnerPort hookRunnerPort() {
        return new LocalShellHookRunner();
    }

    // ── 技能层 ──────────────────────────────────────────────

    @Bean
    public FilesystemSkillProviderPort skillProviderPort(
            HarnessExtensionsProperties extensions,
            @Value("${harness.credentials.home:${user.home}/.dsh}") String defaultDshHome
    ) {
        HarnessExtensionsProperties.Skills skills = extensions.getSkills();
        String configuredHome = skills.getHome();
        Path dshHome = Path.of(configuredHome.isBlank() ? defaultDshHome : configuredHome);
        Path bundledDir = skills.getBundledDir().isBlank() ? null : Path.of(skills.getBundledDir());
        List<Path> customRoots = skills.getRoots().stream()
                .filter(root -> root != null && !root.isBlank())
                .map(Path::of)
                .toList();
        return new FilesystemSkillProviderPort(
                dshHome,
                bundledDir,
                "filesystem",
                customRoots,
                skills.isEnabled(),
                skills.isProjectEnabled()
        );
    }

    // ── LSP 层 ────────────────────────────────────────────────

    // ── 代码运行时层 ───────────────────────────────────────
    /**
     * code Runtime Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Bean
    public ICodeRuntimePort codeRuntimePort() {
        return new LocalCodeRuntimePort();
    }

    // ── ACP 层 ────────────────────────────────────────────────

    @Bean
    public IAcpAgentPort acpAgentPort(
            @Value("${harness.acp.command:}") String command
    ) {
        if (command == null || command.isBlank()) {
            return new StubAcpAgentPort();
        }
        return new StdioAcpAgentPort(command, List.of());
    }

    // ── 子智能体进程外提供方 ────────────────────────

    @Bean
    public AcpSubagentProvider acpSubagentProvider(
            @Value("${harness.subagent.acp.command:}") String command,
            @Value("${harness.agent.default-cwd:#{systemProperties['user.dir']}}") String cwd
    ) {
        if (command == null || command.isBlank()) return null;
        return new AcpSubagentProvider(command, List.of(), cwd, Map.of(), 300_000);
    }

    @Bean
    public CodexSubagentProvider codexSubagentProvider(
            @Value("${harness.subagent.codex.command:codex}") String command,
            @Value("${harness.agent.default-cwd:#{systemProperties['user.dir']}}") String cwd
    ) {
        return new CodexSubagentProvider(command, List.of(), cwd, Map.of(), 300_000);
    }

    @Bean
    public ClaudeCodeSubagentProvider claudeCodeSubagentProvider(
            @Value("${harness.subagent.claude-code.command:claude}") String command,
            @Value("${harness.agent.default-cwd:#{systemProperties['user.dir']}}") String cwd
    ) {
        return new ClaudeCodeSubagentProvider(command, List.of(), cwd, Map.of(), 300_000);
    }

    // ── 工作流层 ───────────────────────────────────────────
    /**
     * workflow Engine Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Bean
    public IWorkflowEnginePort workflowEnginePort() {
        return new LocalWorkflowEnginePort();
    }

    // ── Typert 层 ─────────────────────────────────────────────
    /**
     * typert Registry Contract。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Bean
    public ITypertRegistryContract typertRegistryContract() {
        return new InMemoryTypertRegistry();
    }
    /**
     * typert Protocol Loader。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */


    @Bean
    public TypertProtocolLoader typertProtocolLoader(ObjectMapper objectMapper) {
        return new TypertProtocolLoader(objectMapper);
    }

    // ── 会话投影缓存层 ─────────────────────────────────────

    @Bean
    public ISessionProjectionCache sessionProjectionCache() {
        return new InMemorySessionProjectionCache();
    }

}
