package net.linlan.dsh.cases.goal.node;

import net.linlan.dsh.api.dto.GoalCommandRequestDTO;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;
import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.goal.IGoalCommandService;
import net.linlan.dsh.domain.goal.model.valobj.GoalAction;
import net.linlan.dsh.domain.goal.model.valobj.GoalCommandVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 执行目标创建、推进、挂起等命令。
 * <p>流程：校验会话与动作 → API DTO 转 GoalCommandVO → 领域服务更新 → 转换视图 DTO。</p>
 * <p>数据示例：{@code action=CREATE, title="修复登录", maxRounds=5}；
 * {@code action=BLOCK, blockedReason="等待数据库确认"}。</p>
 */
@Service
public class GoalCommandNode
        extends AbstractStrategyRouter<GoalCommandNode.Input, NoContext, GoalViewResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(GoalCommandNode.class);

    private final IGoalCommandService goalCommandService;

    public GoalCommandNode(IGoalCommandService goalCommandService) {
        this.goalCommandService = goalCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected GoalViewResponseDTO doApply(Input input, NoContext context) {
        CaseGuard.requireSessionId(input.sessionId());
        CaseGuard.requireNonNull(input.request(), "goal request must not be blank");
        CaseGuard.requireText(input.request().action(), "goal action must not be blank");
        var snapshot = goalCommandService.apply(input.sessionId(), toDomain(input.request()));
        log.info("[Goal] 目标命令执行完成，sessionId={} action={} goalId={} status={}",
                input.sessionId(), input.request().action(), snapshot.goalId(), snapshot.status());
        return toResponse(snapshot);
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Input, NoContext, GoalViewResponseDTO> getNext(Input input, NoContext context) {
        return defaultStrategyHandler;
    }

    /**
     * 执行当前节点逻辑，并将领域结果转换为 Pipeline 输出。
     */
    private GoalCommandVO toDomain(GoalCommandRequestDTO request) {
        return new GoalCommandVO(
                GoalAction.valueOf(request.action().trim().toUpperCase(java.util.Locale.ROOT)),
                request.goalId(),
                request.title(),
                request.description(),
                request.blockedReason(),
                request.maxRounds(),
                request.continuationEnabled()
        );
    }

    public record Input(String sessionId, GoalCommandRequestDTO request) {
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
