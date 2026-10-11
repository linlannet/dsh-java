package net.linlan.dsh.domain.goal.service;

import net.linlan.dsh.domain.goal.IGoalCommandService;
import net.linlan.dsh.domain.goal.IGoalQueryService;
import net.linlan.dsh.domain.goal.adapter.repository.IGoalRepository;
import net.linlan.dsh.domain.goal.model.entity.GoalAggregate;
import net.linlan.dsh.domain.goal.model.valobj.GoalAction;
import net.linlan.dsh.domain.goal.model.valobj.GoalCommandVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 目标领域服务。
 * <p>
 * 执行流程：
 * 1. 读取当前会话的目标快照；
 * 2. 依据动作驱动聚合状态机；
 * 3. 将最新快照落盘并返回。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class GoalService implements IGoalQueryService, IGoalCommandService {

    private final IGoalRepository goalRepository;
    private final int defaultMaxRounds;

    /**
     * 初始化目标领域服务。
     * <p>流程：加载目标仓储 → 应用目标状态机 → 写回快照。</p>
     */
    public GoalService(
            IGoalRepository goalRepository,
            @Value("${harness.goal.default-max-rounds:256}") int defaultMaxRounds
    ) {
        this.goalRepository = goalRepository;
        this.defaultMaxRounds = defaultMaxRounds;
    }

    @Override
    public Optional<GoalSnapshotVO> getGoal(String sessionId) {
        return goalRepository.findBySessionId(sessionId).map(GoalAggregate::toSnapshot);
    }
    /**
     * apply。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param command 命令对象。
     * @return 方法执行结果。
     */


    @Override
    public GoalSnapshotVO apply(String sessionId, GoalCommandVO command) {
        validateSession(sessionId);
        GoalCommandVO normalized = normalize(command);
        Instant now = Instant.now();

        Optional<GoalAggregate> currentGoal = goalRepository.findBySessionId(sessionId);
        GoalAggregate updatedGoal;
        if (normalized.action() == GoalAction.CREATE) {
            if (currentGoal.isPresent() && !currentGoal.get().status().isTerminal()) {
                throw new IllegalStateException("active goal already exists for session: " + sessionId);
            }
            long previousRevision = currentGoal.map(GoalAggregate::revision).orElse(0L);
            updatedGoal = GoalAggregate.create(sessionId, normalized, now, previousRevision);
        } else {
            GoalAggregate aggregate = currentGoal.orElseThrow(() ->
                    new IllegalStateException("goal not found for session: " + sessionId));
            updatedGoal = aggregate.transition(normalized, now);
        }

        goalRepository.save(updatedGoal);
        return updatedGoal.toSnapshot();
    }

    /**
     * 规范化目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param command 命令对象。
     * @return 方法执行结果。
     */
    private GoalCommandVO normalize(GoalCommandVO command) {
        if (command == null) {
            throw new IllegalArgumentException("goal command must not be null");
        }
        if (command.action() == null) {
            throw new IllegalArgumentException("goal action must not be blank");
        }
        GoalAction action = command.action();
        Integer maxRounds = command.maxRounds() != null ? command.maxRounds() : defaultMaxRounds;
        Boolean continuationEnabled = command.continuationEnabled();
        return new GoalCommandVO(
                action,
                trimToNull(command.goalId()),
                trimToNull(command.title()),
                trimToNull(command.description()),
                trimToNull(command.blockedReason()),
                maxRounds,
                continuationEnabled == null ? Boolean.TRUE : continuationEnabled
        );
    }

    /**
     * 校验输入与业务规则；不通过时返回失败结果或抛出异常。
     */
    private static void validateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    /**
     * 处理trim To Null。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param value 参数值。
     * @return 方法执行结果。
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
