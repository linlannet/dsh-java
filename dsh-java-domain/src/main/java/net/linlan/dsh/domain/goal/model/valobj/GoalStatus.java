package net.linlan.dsh.domain.goal.model.valobj;

/**
 * 目标当前状态。
 * <p>
 * 执行流程：
 * 1. 聚合根据动作切换状态；
 * 2. 仓储持久化最新快照；
 * 3. 查询接口直接读取当前状态。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public enum GoalStatus {
    ACTIVE,
    PAUSED,
    BLOCKED,
    COMPLETED,
    CLEARED;

    /**
     * 判断是否属于终态。
     * <p>
     * 执行流程：
     * 1. 读取当前状态；
     * 2. 判断是否已结束；
     * 3. 返回布尔结果。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CLEARED;
    }
}
