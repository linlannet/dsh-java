package net.linlan.dsh.domain.agent.service;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.core.waterfall.WaterfallExpert;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;
import net.linlan.dsh.domain.core.waterfall.service.WaterfallChain;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * ScopedSystemPromptAssembler 表示作用域系统提示词的领域装配服务。
 */
public class ScopedSystemPromptAssembler implements SystemPromptAssembler {

    /**
     * 常量字段。
     * <p>用途：保存该字段对应的领域状态或配置。</p>
     */
    public static final String PERSONA_SECTION = "deployment:persona";

    /**
     * 常量字段。
     * <p>用途：保存该字段对应的领域状态或配置。</p>
     */
    public static final int PERSONA_ORDER = 0;

    // ── Global registry ──────────────────────────────────────────
    private final Map<String, PromptSection> sections = new LinkedHashMap<>();
    private final Map<String, PromptContext> contexts = new LinkedHashMap<>();
    private final List<Function<AssembleContext, ToolProviderResult>> toolProviders = new ArrayList<>();
    private final Map<String, Function<AssembleContext, String>> variables = new LinkedHashMap<>();
    private final List<Runnable> runtimeContextSuppressors = new ArrayList<>();

    private final String harnessIdentity;
    private final String persona;
    private final List<String> toolOrder;

    /**
     * 处理Scoped System 提示词 Assembler。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param harnessIdentity harness Identity参数。
     * @param persona persona参数。
     * @param toolOrder 工具 Order参数。
     * @return 方法执行结果。
     */
    public ScopedSystemPromptAssembler(String harnessIdentity, String persona, List<String> toolOrder) {
        this.harnessIdentity = harnessIdentity != null ? harnessIdentity
                : "You are an AI agent powered by DeepSeek Harness.";
        this.persona = persona != null ? persona : "";
        this.toolOrder = toolOrder;

        // Register fixed sections
        registerSection(new PromptSection("harness:identity", -100, this.harnessIdentity));
        registerSection(new PromptSection(PERSONA_SECTION, PERSONA_ORDER, this.persona));
    }

    /**
     * 构造ScopedSystemPromptAssembler领域对象。
     */
    public ScopedSystemPromptAssembler() {
        this(null, null, List.of());
    }

    // ── Registration API ─────────────────────────────────────────

    /**
     * 注册 Section。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param section section参数。
     */
    public synchronized void registerSection(PromptSection section) {
        sections.put(section.name(), section);
    }

    /**
     * 移除 Section。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name 名称。
     */
    public synchronized void removeSection(String name) {
        sections.remove(name);
    }

    /**
     * 注册 上下文。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param context 上下文对象。
     */
    public synchronized void registerContext(PromptContext context) {
        contexts.put(context.name(), context);
    }

    /**
     * 移除 上下文。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name 名称。
     */
    public synchronized void removeContext(String name) {
        contexts.remove(name);
    }

    /**
     * 注册 工具 模型提供方。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param Function<AssembleContext Function Assemble 上下文参数。
     * @param provider 模型提供方。
     */
    public synchronized void registerToolProvider(Function<AssembleContext, ToolProviderResult> provider) {
        toolProviders.add(provider);
    }

    /**
     * 注册 Variable。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name 名称。
     * @param Function<AssembleContext Function Assemble 上下文参数。
     * @param provider 模型提供方。
     */
    public synchronized void registerVariable(String name, Function<AssembleContext, String> provider) {
        if (!isValidVariableName(name)) {
            throw new IllegalArgumentException("invalid variable name: " + name);
        }
        variables.put(name, provider);
    }

    /**
     * 处理suppress 运行时 上下文。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     */
    public synchronized void suppressRuntimeContext() {
        runtimeContextSuppressors.add(() -> {});
    }

    // ── Assembly ────────────────────────────────────────────────

    @Override
    public CompletableFuture<PromptAssembly> assemble(
            String agentId,
            AgentOptions options,
            String cwd,
            List<ToolSchema> tools
    ) {
        AssembleContext ctx = new AssembleContext(agentId, options, cwd, null);

        // Resolve sections via waterfall chain
        List<PromptSection> sorted = new ArrayList<>(sections.values());
        sorted.sort(Comparator.comparingInt(PromptSection::order));

        List<WaterfallExpert<PromptFragment>> sectionExperts = new ArrayList<>();
        for (PromptSection sec : sorted) {
            sectionExperts.add(new SectionExpert(sec, ctx));
        }
        var chain = new WaterfallChain<>(sectionExperts);
        var fragmentResult = chain.execute(
                WaterfallEvent.<PromptFragment>of("system-prompt/assemble", null),
                PromptFragment.empty());

        List<AssembledSection> resolvedSections = fragmentResult.sections();
        boolean hasComplete = fragmentResult.terminated();
        AssembledSection completeSection = hasComplete && !resolvedSections.isEmpty()
                ? resolvedSections.get(resolvedSections.size() - 1) : null;

        // Resolve contexts
        boolean runtimeSuppressed = !runtimeContextSuppressors.isEmpty();
        List<AssembledContext> resolvedContexts = new ArrayList<>();
        if (!runtimeSuppressed) {
            List<PromptContext> sortedCtx = new ArrayList<>(contexts.values());
            sortedCtx.sort(Comparator.comparingInt(PromptContext::order));
            for (PromptContext c : sortedCtx) {
                String text = c.text() instanceof String s ? s
                        : c.text() instanceof Function f ? (String) f.apply(ctx) : "";
                resolvedContexts.add(new AssembledContext(c.name(), text));
            }
        }

        // Collect tool schemas
        List<ToolSchema> collected = new ArrayList<>(tools != null ? tools : List.of());
        for (Function<AssembleContext, ToolProviderResult> provider : toolProviders) {
            ToolProviderResult result = provider.apply(ctx);
            collected.addAll(result.schemas());
        }
        List<ToolSchema> orderedTools = orderTools(collected);

        // Resolve variables
        Map<String, String> resolvedVars = new LinkedHashMap<>();
        for (Map.Entry<String, Function<AssembleContext, String>> entry : variables.entrySet()) {
            resolvedVars.put(entry.getKey(), entry.getValue().apply(ctx));
        }

        // Build the PromptAssembly
        PromptAssemblyFull assembly = new PromptAssemblyFull(
                resolvedSections,
                resolvedContexts,
                orderedTools,
                resolvedVars
        );

        // Render
        String system = renderPrompt(assembly);
        String runtimeContext = renderContextSnapshot(assembly);
        if (!runtimeContext.isEmpty()) {
            system = runtimeContext + "\n\n" + system;
        }

        // If complete section, use only that
        if (hasComplete && completeSection != null) {
            system = interpolate(completeSection, resolvedVars);
        }

        return CompletableFuture.completedFuture(new SystemPromptAssembler.PromptAssembly(system, orderedTools));
    }

    // ── Rendering ───────────────────────────────────────────────

    /**
     * 处理renderPrompt提示词。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param assembly assembly。
     * @return 领域处理结果。
     */
    public static String renderPrompt(PromptAssemblyFull assembly) {
        return assembly.sections().stream()
                .map(s -> interpolate(s, assembly.variables()))
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    /**
     * 处理renderContextSnapshot。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param assembly assembly。
     * @return 领域处理结果。
     */
    public static String renderContextSnapshot(PromptAssemblyFull assembly) {
        String body = assembly.contexts().stream()
                .map(c -> interpolate(c, assembly.variables()))
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        if (body.isEmpty()) return "";
        return "Current runtime context. This snapshot supersedes earlier runtime-context snapshots.\n\n" + body;
    }

    /**
     * 处理interpolate。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param input input。
     * @param variables variable列表。
     * @return 领域处理结果。
     */
    private static String interpolate(AssembledSection input, Map<String, String> variables) {
        return doInterpolate(input.text(), variables, "section", input.name());
    }

    /**
     * 插值目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param input 输入内容。
     * @param Map<String 映射 String参数。
     * @param variables variables参数。
     * @return 方法执行结果。
     */
    private static String interpolate(AssembledContext input, Map<String, String> variables) {
        return doInterpolate(input.text(), variables, "context", input.name());
    }

    /**
     * 处理do Interpolate。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param text text参数。
     * @param Map<String 映射 String参数。
     * @param variables variables参数。
     * @param kind kind参数。
     * @param name 名称。
     * @return 方法执行结果。
     */
    private static String doInterpolate(String text, Map<String, String> variables, String kind, String name) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        int last = 0;
        int open = text.indexOf("{{");
        while (open >= 0) {
            int close = text.indexOf("}}", open + 2);
            if (close < 0) {
                result.append(text, last, open + 2);
                break;
            }
            String varName = text.substring(open + 2, close).trim();
            if (varName.isEmpty()) {
                throw new IllegalArgumentException("malformed prompt variable reference in " + kind + " \"" + name + "\"");
            }
            if (!variables.containsKey(varName)) {
                // Unknown variable — leave literal
                result.append(text, last, close + 2);
            } else {
                String value = variables.get(varName);
                if (value == null) {
                    throw new IllegalArgumentException("prompt variable \"" + varName + "\" has no value in " + kind + " \"" + name + "\"");
                }
                result.append(text, last, open);
                result.append(value);
            }
            last = close + 2;
            open = text.indexOf("{{", last);
        }
        result.append(text, last, text.length());
        return result.toString();
    }

    // ── Tool ordering ───────────────────────────────────────────

    private static final String TOOL_ORDER_REST = "<unlisted-tools>";

    /**
     * 处理order Tools。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param tools 工具列表。
     * @return 方法执行结果。
     */
    private List<ToolSchema> orderTools(List<ToolSchema> tools) {
        if (toolOrder == null || toolOrder.isEmpty()) {
            return tools.stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
        }
        List<String> order = new ArrayList<>(toolOrder);
        if (!order.contains(TOOL_ORDER_REST)) {
            return tools.stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
        }
        var listed = new java.util.HashSet<String>(order);
        var rest = tools.stream().filter(t -> !listed.contains(t.name()))
                .sorted((a, b) -> a.name().compareTo(b.name())).toList();
        List<ToolSchema> result = new ArrayList<>();
        for (String name : order) {
            if (name.equals(TOOL_ORDER_REST)) {
                result.addAll(rest);
            } else {
                tools.stream().filter(t -> t.name().equals(name)).findFirst().ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * 判断Valid Variable Name是否成立。
     */
    private static boolean isValidVariableName(String name) {
        return name != null && name.matches("^[a-z][a-z0-9_]*$");
    }

    // ── Types ───────────────────────────────────────────────────

    /**
     * PromptSection 表示提示词PromptSection。
     */
    public record PromptSection(
            String name,
            int order,
            Object text, // String or Function<AssembleContext, String>
            boolean complete
    ) {
        /**
         * 发起提示词 Section。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param name 名称。
         * @param order 排序规则。
         * @param text text参数。
         * @return 方法执行结果。
         */
        public PromptSection(String name, int order, String text) {
            this(name, order, text, false);
        }
        /**
         * 发起提示词 Section。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param name 名称。
         * @param order 排序规则。
         * @param text text参数。
         * @return 方法执行结果。
         */
        public PromptSection(String name, int order, Object text) {
            this(name, order, text, false);
        }
    }

    /**
     * PromptContext 表示提示词PromptContext。
     */
    public record PromptContext(
            String name,
            int order,
            Object text // String or Function<AssembleContext, String>
    ) {}

    /**
     * AssembledSection 表示AssembledSection。
     */
    public record AssembledSection(String name, String text) {}

    /**
     * AssembledContext 表示AssembledContext。
     */
    public record AssembledContext(String name, String text) {}

    /**
     * ToolProviderResult 表示ToolProviderResult提供方结果。
     */
    public record ToolProviderResult(
            List<ToolSchema> schemas,
            List<String> knownNames
    ) {
        /**
         * 转换为 ol 模型提供方 结果。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param schemas schemas参数。
         * @return 方法执行结果。
         */
        public ToolProviderResult(List<ToolSchema> schemas) {
            this(schemas, schemas.stream().map(ToolSchema::name).toList());
        }
    }

    /**
     * PromptAssemblyFull 表示提示词PromptAssemblyFull。
     */
    public record PromptAssemblyFull(
            List<AssembledSection> sections,
            List<AssembledContext> contexts,
            List<ToolSchema> tools,
            Map<String, String> variables
    ) {}

    /**
     * AssembleContext 表示AssembleContext。
     */
    public record AssembleContext(
            String agentId,
            AgentOptions options,
            String cwd,
            Object signal
    ) {}

    /**
     * PromptFragment 是 waterfall 链中传递的提示词片段累积器。
     * @param sections  已解析的 section 列表
     * @param terminated 是否被 complete 专家终止
     */
    record PromptFragment(List<AssembledSection> sections, boolean terminated) {

        static PromptFragment empty() {
            return new PromptFragment(List.of(), false);
        }

        PromptFragment append(AssembledSection section) {
            var list = new ArrayList<>(sections);
            list.add(section);
            return new PromptFragment(List.copyOf(list), false);
        }
    }

    /**
     * SectionExpert 将一个 PromptSection 包装为 waterfall 专家。
     * priority=section.order，canTerminate=section.complete。
     */
    private static final class SectionExpert implements WaterfallExpert<PromptFragment> {

        private final PromptSection section;
        private final AssembleContext ctx;

        SectionExpert(PromptSection section, AssembleContext ctx) {
            this.section = section;
            this.ctx = ctx;
        }

        @Override
        public int priority() {
            return section.order();
        }

        @Override
        public boolean canTerminate() {
            return section.complete();
        }

        @Override
        public WaterfallOutcome<PromptFragment> handle(WaterfallEvent<PromptFragment> event, PromptFragment current) {
            String text = section.text() instanceof String s ? s
                    : section.text() instanceof Function f ? (String) f.apply(ctx) : "";
            var assembled = new AssembledSection(section.name(), text);
            if (section.complete()) {
                return WaterfallOutcome.terminateWith(new PromptFragment(List.of(assembled), true));
            }
            return WaterfallOutcome.continueWith(current.append(assembled));
        }
    }
}
