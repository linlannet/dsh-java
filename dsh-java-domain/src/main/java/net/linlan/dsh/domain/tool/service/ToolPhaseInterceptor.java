package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.tool.model.valobj.AroundWrapper;
import net.linlan.dsh.domain.tool.model.valobj.GuardDecision;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhase;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhaseOutcome;

/**
 * ToolPhaseInterceptor 是五阶段工具管线的拦截器。
 * <p>每个拦截器声明所属 {@link #phase()} 和 {@link #priority()}，
 * 并按阶段提供对应的 default 方法（默认不干预）。</p>
 */
public interface ToolPhaseInterceptor {

    /**
     * 所属阶段。
     */
    ToolPhase phase();

    /**
     * 优先级（同阶段内升序执行，值越小越先执行）。
     */
    int priority();

    /**
     * PRE 阶段：前置拦截，可短路。
     */
    default ToolPhaseOutcome preIntercept(ToolCallContext ctx) {
        return ToolPhaseOutcome.proceed(ToolPhase.PRE);
    }

    /**
     * GUARD 阶段：守卫拦截，可拒绝。
     */
    default GuardDecision guardIntercept(ToolCallContext ctx) {
        return GuardDecision.allow();
    }

    /**
     * AROUND 阶段：环绕包装，可控制是否调用主体。
     */
    default AroundWrapper aroundIntercept(ToolCallContext ctx) {
        return AroundWrapper.passThrough();
    }

    /**
     * POST 阶段：后置拦截，可短路。
     */
    default ToolPhaseOutcome postIntercept(ToolCallContext ctx, ToolExecutionResult result) {
        return ToolPhaseOutcome.proceed(ToolPhase.POST);
    }

    /**
     * RESULT 阶段：结果拦截，可改写结果。
     */
    default ToolExecutionResult resultIntercept(ToolCallContext ctx, ToolExecutionResult result) {
        return result;
    }
}
