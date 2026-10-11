package net.linlan.dsh.domain.tool.service;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.tool.model.valobj.GuardDecision;
import net.linlan.dsh.domain.tool.model.valobj.ToolPhase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * ToolPipeline 是五阶段工具执行管线。
 * <p>按 PRE → GUARD → AROUND → POST → RESULT 固定顺序执行拦截器。</p>
 * <ul>
 *   <li>PRE：前置拦截，可短路（不执行工具）</li>
 *   <li>GUARD：守卫拦截，拒绝则不执行主体，返回拒绝结果</li>
 *   <li>AROUND：洋葱模型嵌套包裹主体，可控制是否调用</li>
 *   <li>POST：后置拦截，可短路</li>
 *   <li>RESULT：结果拦截，可改写最终结果</li>
 * </ul>
 */
public class ToolPipeline {

    private final List<ToolPhaseInterceptor> interceptors;

    /**
     * 创建管线。
     */
    public ToolPipeline(List<ToolPhaseInterceptor> interceptors) {
        this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
    }

    /**
     * 执行五阶段管线。
     *
     * @param ctx      工具调用上下文
     * @param toolBody 工具主体（实际执行工具逻辑的回调）
     * @return 工具执行结果
     */
    public ToolExecutionResult execute(ToolCallContext ctx, Supplier<ToolExecutionResult> toolBody) {

        // ── PRE ──────────────────────────────────────────
        for (var interceptor : interceptorsOf(ToolPhase.PRE)) {
            var outcome = interceptor.preIntercept(ctx);
            if (!outcome.shouldProceed()) {
                return outcome.resultIfHalted();
            }
        }

        // ── GUARD ────────────────────────────────────────
        for (var interceptor : interceptorsOf(ToolPhase.GUARD)) {
            var decision = interceptor.guardIntercept(ctx);
            if (decision instanceof GuardDecision.Deny deny) {
                return ToolExecutionResult.fail(deny.reason(), deny.errorCode());
            }
        }

        // ── AROUND（洋葱模型，priority 升序从外到内嵌套）─────────
        Supplier<ToolExecutionResult> chain = toolBody;
        var arounds = interceptorsOf(ToolPhase.AROUND);
        for (var around : arounds) {
            var wrapper = around.aroundIntercept(ctx);
            var next = chain;
            chain = () -> wrapper.wrap(next);
        }
        ToolExecutionResult result = chain.get();

        // ── POST ─────────────────────────────────────────
        for (var interceptor : interceptorsOf(ToolPhase.POST)) {
            var outcome = interceptor.postIntercept(ctx, result);
            if (!outcome.shouldProceed()) {
                return outcome.resultIfHalted();
            }
        }

        // ── RESULT ───────────────────────────────────────
        for (var interceptor : interceptorsOf(ToolPhase.RESULT)) {
            result = interceptor.resultIntercept(ctx, result);
        }

        return result;
    }

    /**
     * 返回指定阶段的拦截器（按 priority 升序）。
     */
    private List<ToolPhaseInterceptor> interceptorsOf(ToolPhase phase) {
        var filtered = new ArrayList<ToolPhaseInterceptor>();
        for (var interceptor : interceptors) {
            if (interceptor.phase() == phase) {
                filtered.add(interceptor);
            }
        }
        filtered.sort(Comparator.comparingInt(ToolPhaseInterceptor::priority));
        return filtered;
    }
}
