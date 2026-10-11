package net.linlan.dsh.domain.tool.service;

import java.util.Map;

/**
 * 运行期工具审批门禁。
 * <p>
 * 在 {@code ToolCallExecutor} 每次派发工具前判定该调用是否被允许。
 * 对齐原版 TS 的 {@code approval.request(...)} 运行期拦截语义：
 * allow / allow-session（会话级记忆放行）/ deny，以及 ask（实时挂起等待人工裁决）。
 * <p>
 * 默认实现 {@link #allowAll()} 放行一切，保持无审批配置时的既有行为；
 * 由装配层（AgentRunFactory）按权限矩阵注入真实实现。
 *
 *
 */
@FunctionalInterface
public interface RuntimeApprovalGate {

    /**
     * Decision 定义当前模块中的enum契约与职责。
     */
    enum Decision { ALLOW, DENY }

    /**
     * 判定一次工具调用是否允许执行。
     *
     * @param toolName  工具名
     * @param arguments 已解析的参数（可用于细粒度判定，如按命令内容放行）
     * @return 决策结果
     */
    Decision check(String toolName, Object arguments);

    /**
     * 判断指定工具是否需要审批（默认实现一律不需要）。供装配层/执行器提前分流。
     *
     * @param toolName 工具名
     * @return true 表示需要审批
     */
    default boolean requiresApproval(String toolName) {
        return false;
    }

    /**
     * 需要人工裁决时的交互入口。默认实现直接 DENY（防御性）；
     * 装配层注入可阻塞等待用户选择的实现（对应 TS 的 gate.kind === 'ask'）。
     *
     * @param request 待裁决的工具调用详情
     * @return 用户裁决结果
     */
    default ApprovalVerdict ask(ApprovalRequest request) {
        return ApprovalVerdict.DENY;
    }

    /** 放行一切（默认）。 */
    static RuntimeApprovalGate allowAll() {
        return (toolName, arguments) -> Decision.ALLOW;
    }

    /** 一次待审批的工具调用。 */
    record ApprovalRequest(
            String sessionId,
            String toolName,
            String displayCommand,
            Map<String, Object> arguments
    ) {}

    /** 用户裁决：放行一次 / 本会话内放行该工具 / 拒绝 / 取消整个任务。 */
    enum ApprovalVerdict { ALLOW_ONCE, ALLOW_SESSION, DENY, CANCEL }
}
