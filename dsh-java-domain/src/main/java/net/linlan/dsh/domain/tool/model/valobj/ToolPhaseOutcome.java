package net.linlan.dsh.domain.tool.model.valobj;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;

/**
 * ToolPhaseOutcome 是 PRE/POST 阶段拦截的输出。
 * <p>shouldProceed=true 表示继续管线；shouldProceed=false 表示短路并返回 resultIfHalted。</p>
 *
 * @param phase           产生此输出的阶段
 * @param shouldProceed   是否继续管线
 * @param resultIfHalted  短路时返回的结果（shouldProceed=false 时使用）
 */
public record ToolPhaseOutcome(ToolPhase phase, boolean shouldProceed, ToolExecutionResult resultIfHalted) {

    /**
     * 创建继续管线的输出。
     */
    public static ToolPhaseOutcome proceed(ToolPhase phase) {
        return new ToolPhaseOutcome(phase, true, null);
    }

    /**
     * 创建短路管线的输出。
     */
    public static ToolPhaseOutcome halt(ToolPhase phase, ToolExecutionResult result) {
        return new ToolPhaseOutcome(phase, false, result);
    }
}
