package net.linlan.dsh.cases.goal;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.dto.GoalCommandRequestDTO;
import net.linlan.dsh.cases.goal.factory.GoalPipelineFactory;
import net.linlan.dsh.cases.goal.node.GoalCommandNode;
import org.springframework.stereotype.Service;

/**
 * 目标命令编排实现。
 * <p>流程：合并 sessionId 与请求 → 进入 Goal Command Pipeline → 返回目标快照 DTO。</p>
 * <p>数据示例：传入 {@code sessionId=session-001} 和 {@code action=UPDATE}，
 * 返回更新后的 {@code GoalViewResponseDTO}。</p>
 */
@Service
public class GoalCommandCaseImpl implements IGoalCommandCase {
    private final GoalPipelineFactory goalPipelineFactory;

    public GoalCommandCaseImpl(GoalPipelineFactory goalPipelineFactory) {
        this.goalPipelineFactory = goalPipelineFactory;
    }

    /**
     * 处理当前用例步骤，完成必要校验并返回执行结果。
     */
    @Override
    public GoalViewResponseDTO apply(String sessionId, GoalCommandRequestDTO request) {
        return goalPipelineFactory.commandPipeline()
                .execute(new GoalCommandNode.Input(sessionId, request));
    }
}
