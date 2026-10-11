package net.linlan.dsh.domain.goal;

import net.linlan.dsh.domain.goal.model.valobj.GoalCommandVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;

/**
 * 目标命令服务接口。
 * <p>
 * 执行流程：
 * 1. 上层传入 sessionId 与动作命令；
 * 2. 领域层驱动目标状态机；
 * 3. 返回最新目标快照。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IGoalCommandService {

    /**
     * 执行目标状态变更动作。
     * <p>
     * 执行流程：
     * 1. 读取当前目标；
     * 2. 按动作更新聚合；
     * 3. 持久化并返回最新快照。
     */
    GoalSnapshotVO apply(String sessionId, GoalCommandVO command);
}
