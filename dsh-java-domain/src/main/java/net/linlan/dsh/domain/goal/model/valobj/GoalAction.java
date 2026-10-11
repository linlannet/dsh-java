package net.linlan.dsh.domain.goal.model.valobj;

/**
 * 目标变更动作。
 * <p>
 * 执行流程：
 * 1. API 层接收动作字符串；
 * 2. 领域层映射为受控枚举；
 * 3. 聚合根据动作推进状态机。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public enum GoalAction {
    CREATE,
    UPDATE,
    PAUSE,
    RESUME,
    COMPLETE,
    BLOCK,
    CLEAR
}
