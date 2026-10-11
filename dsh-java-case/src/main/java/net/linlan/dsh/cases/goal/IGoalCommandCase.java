package net.linlan.dsh.cases.goal;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.dto.GoalCommandRequestDTO;

/**
 * 目标命令用例契约。
 * <p>流程：Trigger → GoalCommandCase → Pipeline → GoalCommandNode → 领域目标服务。</p>
 * <p>数据示例：{@code sessionId=session-001, action=CREATE, title="修复登录"}
 * 创建目标；{@code action=BLOCK} 挂起目标。</p>
 */
public interface IGoalCommandCase {
    GoalViewResponseDTO apply(String sessionId, GoalCommandRequestDTO request);
}
