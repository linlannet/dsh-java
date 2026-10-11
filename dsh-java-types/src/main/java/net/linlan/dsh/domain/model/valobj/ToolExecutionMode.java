package net.linlan.dsh.domain.model.valobj;

/**
 * 待执行工具调用的调度模式。
 * 对应 TS 侧 {@code ToolExecutionMode}。
 *
 * <ul>
 *   <li>{@link #PARALLEL} — 可以在有界线程池中与同批调用并行执行</li>
 *   <li>{@link #EXCLUSIVE} — 单独执行，并形成顺序屏障</li>
 * </ul>
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public enum ToolExecutionMode {
    PARALLEL,
    EXCLUSIVE
}
