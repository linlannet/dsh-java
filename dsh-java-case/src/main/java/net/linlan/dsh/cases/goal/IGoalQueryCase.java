package net.linlan.dsh.cases.goal;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;

/**
 * 目标查询用例契约。
 * <p>流程：Trigger → GoalQueryCase → Pipeline → GoalQueryNode → 领域查询服务。</p>
 * <p>数据示例：传入 {@code sessionId=session-001}，返回目标标题、状态、轮次等快照；
 * 不存在时返回 {@code exists=false} 的空目标。</p>
 */
public interface IGoalQueryCase {
    GoalViewResponseDTO getGoal(String sessionId);
}
