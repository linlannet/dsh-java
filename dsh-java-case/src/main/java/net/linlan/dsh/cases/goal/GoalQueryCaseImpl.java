package net.linlan.dsh.cases.goal;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.cases.goal.factory.GoalPipelineFactory;
import org.springframework.stereotype.Service;

/**
 * 目标查询编排实现。
 * <p>流程：使用 sessionId 进入 Goal Query Pipeline → 查询领域快照 → 转换 DTO。</p>
 * <p>数据示例：{@code sessionId=session-001} 返回当前目标；无目标时返回
 * {@code exists=false} 的空快照。</p>
 */
@Service
public class GoalQueryCaseImpl implements IGoalQueryCase {
    private final GoalPipelineFactory goalPipelineFactory;

    public GoalQueryCaseImpl(GoalPipelineFactory goalPipelineFactory) {
        this.goalPipelineFactory = goalPipelineFactory;
    }

    /**
     * 读取上下文中的当前值。
     */
    @Override
    public GoalViewResponseDTO getGoal(String sessionId) {
        return goalPipelineFactory.queryPipeline().execute(sessionId);
    }
}
