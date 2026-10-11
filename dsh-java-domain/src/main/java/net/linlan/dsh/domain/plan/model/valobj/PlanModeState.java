package net.linlan.dsh.domain.plan.model.valobj;

/**
 * 计划模式状态。
 * <p>
 * 执行流程：
 * 1. 会话进入计划阶段时切换到 `Planning`；
 * 2. 用户提交计划后切换到 `Approved`；
 * 3. 默认回到 `Off`。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public sealed interface PlanModeState permits
        PlanModeState.Off,
        PlanModeState.Planning,
        PlanModeState.Approved {

    /** 正常模式。 */
    record Off() implements PlanModeState {}

    /** 计划中。 */
    record Planning() implements PlanModeState {}

    /** 计划已提交等待批准。 */
    record Approved(String plan) implements PlanModeState {}
}
