package net.linlan.dsh.domain.goal;

import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;
import java.util.Optional;

/**
 * 目标查询服务接口。
 * <p>
 * 执行流程：
 * 1. 上层传入 sessionId；
 * 2. 领域层读取当前目标快照；
 * 3. 返回可选结果给调用方。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IGoalQueryService {

    /**
     * 查询当前目标。
     * <p>
     * 执行流程：
     * 1. 定位会话；
     * 2. 读取目标快照；
     * 3. 返回可选结果。
     */
    Optional<GoalSnapshotVO> getGoal(String sessionId);
}
