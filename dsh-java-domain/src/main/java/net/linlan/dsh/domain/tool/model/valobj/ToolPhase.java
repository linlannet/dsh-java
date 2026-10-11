package net.linlan.dsh.domain.tool.model.valobj;

/**
 * ToolPhase 定义工具执行管线的五个阶段，固定执行顺序。
 * <p>PRE → GUARD → AROUND → POST → RESULT</p>
 */
public enum ToolPhase {

    /** 前置拦截：在工具执行前调用，可短路（不执行工具）。 */
    PRE,

    /** 守卫拦截：在工具执行前调用，可拒绝（不执行工具，返回拒绝结果）。 */
    GUARD,

    /** 环绕拦截：洋葱模型包裹工具主体，可控制是否调用主体、可在前后注入逻辑。 */
    AROUND,

    /** 后置拦截：在工具执行后调用，可短路（不执行后续阶段）。 */
    POST,

    /** 结果拦截：在工具执行后调用，可改写最终结果。 */
    RESULT
}
