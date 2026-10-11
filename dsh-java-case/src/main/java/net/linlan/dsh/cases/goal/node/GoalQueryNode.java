package net.linlan.dsh.cases.goal.node;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.goal.IGoalQueryService;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 查询指定会话当前的目标快照。
 * <p>流程：校验 sessionId → 领域查询服务读取快照 → 转换 GoalViewResponseDTO。</p>
 * <p>数据示例：{@code session-001} 可返回 {@code status=RUNNING, roundsStarted=2}；
 * 没有目标时返回空目标视图。</p>
 */
@Service
public class GoalQueryNode extends AbstractStrategyRouter<String, NoContext, GoalViewResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(GoalQueryNode.class);

    private final IGoalQueryService goalQueryService;

    public GoalQueryNode(IGoalQueryService goalQueryService) {
        this.goalQueryService = goalQueryService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected GoalViewResponseDTO doApply(String sessionId, NoContext context) {
        CaseGuard.requireSessionId(sessionId);
        var goal = goalQueryService.getGoal(sessionId);
        log.debug("[Goal] 目标查询完成，sessionId={} present={}", sessionId, goal.isPresent());
        return goal
                .map(this::toResponse)
                .orElseGet(() -> GoalViewResponseDTO.empty(sessionId));
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<String, NoContext, GoalViewResponseDTO> getNext(String sessionId, NoContext context) {
        return defaultStrategyHandler;
    }

    /**
     * 将领域层结果对象转换为用例层 DTO。
     */
    private GoalViewResponseDTO toResponse(GoalSnapshotVO snapshot) {
        return new GoalViewResponseDTO(
                snapshot.sessionId(), snapshot.goalId(), snapshot.revision(), snapshot.title(),
                snapshot.description(), snapshot.status().name(), snapshot.blockedReason(),
                snapshot.maxRounds(), snapshot.roundsStarted(), snapshot.continuationEnabled(),
                snapshot.createdAt(), snapshot.updatedAt(), true
        );
    }
}
