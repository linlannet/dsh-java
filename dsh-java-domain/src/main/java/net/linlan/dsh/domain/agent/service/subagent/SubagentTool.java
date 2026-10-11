package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * SubagentTool 表示子智能体调用的领域工具定义。
 */
public class SubagentTool implements ToolDefinition {

    private static final String DEFAULT_TOOL_NAME = "subagent";

    private final String toolName;
    private final SubagentRegistry registry;
    private final String providerName;
    private final Supplier<AgentOptions> parentOptionsSupplier;

    /**
     * 初始化子智能体工具。
     * <p>流程：注册可用提供者 → 校验工具参数 → 调用目标子智能体。</p>
     */
    public SubagentTool(
            SubagentRegistry registry,
            String providerName
    ) {
        this(registry, providerName, DEFAULT_TOOL_NAME);
    }

    /**
     * 初始化指定提供者名称的子智能体工具。
     * <p>流程：按 providerName 选择提供者 → 校验工具参数 → 调用目标子智能体。</p>
     */
    public SubagentTool(
            SubagentRegistry registry,
            String providerName,
            String toolName
    ) {
        this(registry, providerName, toolName, (Supplier<AgentOptions>) null);
    }

    /**
     * 初始化从父 Agent 动态读取模型渠道的子智能体工具。
     */
    public SubagentTool(
            SubagentRegistry registry,
            String providerName,
            String toolName,
            Supplier<AgentOptions> parentOptionsSupplier
    ) {
        this.registry = registry;
        this.providerName = providerName;
        this.toolName = toolName;
        this.parentOptionsSupplier = parentOptionsSupplier;
    }

    @Override
    public String name() {
        return toolName;
    }
    /**
     * 返回 description。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public String description() {
        return """
                Delegate a task to a subagent (child agent). The subagent runs in its own \
                session with its own context and tools. Use this for complex subtasks that \
                benefit from focused multi-step reasoning. The subagent's final answer is \
                returned as this tool's result.""";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> promptProp = new LinkedHashMap<>();
        promptProp.put("type", "string");
        promptProp.put("description", "The task description / prompt for the subagent.");
        properties.put("prompt", promptProp);

        Map<String, Object> bgProp = new LinkedHashMap<>();
        bgProp.put("type", "boolean");
        bgProp.put("description", "If true, run the subagent in the background (default: false).");
        properties.put("run_in_background", bgProp);

        props.put("properties", properties);
        props.put("required", List.of("prompt"));

        return props;
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of();
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param exec exec参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext exec) {
        Map<String, Object> argMap;
        if (args instanceof Map) {
            argMap = (Map<String, Object>) args;
        } else {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("Invalid args type", "BAD_ARGS")
            );
        }

        String prompt = argMap.get("prompt") instanceof String s ? s : "";
        if (prompt.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("prompt is required", "MISSING_PROMPT")
            );
        }

        SubagentProvider provider = registry.resolve(providerName);

        SubagentProvider.SubagentStartRequest request = new SubagentProvider.SubagentStartRequest(
                prompt,
                parentOptionsSupplier == null ? null : parentOptionsSupplier.get(),
                null,           // parentSessionId
                null,           // parentCwd
                0,              // delegationDepth (top-level)
                List.of(),      // enabledTools (all)
                List.of()       // allowedToolNames (all)
        );

        return provider.start(request).<ToolExecutionResult>thenApply(result -> {
            List<ContentBlock> content;
            if (result.output() != null && !result.output().isEmpty()) {
                content = result.output();
            } else {
                content = List.of(new TextBlock(
                        "[subagent ended with reason: " + result.stopReason() + " and no output]"
                ));
            }

            if ("error".equals(result.stopReason()) || "aborted".equals(result.stopReason())) {
                return ToolExecutionResult.fail(
                        "Subagent ended with reason: " + result.stopReason(),
                        "SUBAGENT_" + result.stopReason().toUpperCase()
                );
            }

            return ToolExecutionResult.ok(content);
        }).exceptionally(e -> ToolExecutionResult.fail(
                "Subagent execution failed: " + e.getMessage(),
                "SUBAGENT_ERROR"
        ));
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) {
        // Subagent runs are exclusive — they create child agents
        return false;
    }
    /**
     * 返回 timeout Ms。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public long timeoutMs() {
        return 300_000; // 5 minutes default
    }
}
