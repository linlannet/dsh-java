package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SubagentProvider 表示创建子智能体的领域端口。
 */
public interface SubagentProvider {

    /**
     * 处理名称。
     * @return 领域处理结果。
     */
    String name();

    /**
     * 启动生命周期或异步任务。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param request 请求。
     * @return 领域处理结果。
     */
    CompletableFuture<SubagentResult> start(SubagentStartRequest request);

    /**
     * SubagentStartRequest 表示SubagentStartRequest请求。
     */
    SubagentCapabilities capabilities();

    // ── Types ───────────────────────────────────────────────────

    /**
     * SubagentStartRequest 定义当前模块中的record契约与职责。
     */
    record SubagentStartRequest(
            String prompt,
            AgentOptions options,
            String parentSessionId,
            String parentCwd,
            int delegationDepth,
            List<ToolSchema> enabledTools,
            List<String> allowedToolNames
    ) {}

    /**
     * SubagentCapabilities 定义当前模块中的record契约与职责。
     */
    record SubagentCapabilities(
            boolean continuable,
            boolean structuredOutput,
            boolean localAgent
    ) {}

    /**
     * SubagentResult 定义当前模块中的record契约与职责。
     */
    record SubagentResult(
            String stopReason,      // "completed" | "aborted" | "error" | "max-tokens"
            List<ContentBlock> output,
            String runId
    ) {}
}
