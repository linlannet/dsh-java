package net.linlan.dsh.domain.goal.model.entity;

import net.linlan.dsh.domain.goal.model.valobj.GoalAction;
import net.linlan.dsh.domain.goal.model.valobj.GoalCommandVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalStatus;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 目标聚合根。
 * <p>
 * 执行流程：
 * 1. 创建时初始化当前目标快照；
 * 2. 变更时校验状态机约束；
 * 3. 输出可持久化的最新快照。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record GoalAggregate(
        String sessionId,
        String goalId,
        long revision,
        String title,
        String description,
        GoalStatus status,
        String blockedReason,
        int maxRounds,
        int roundsStarted,
        boolean continuationEnabled,
        Instant createdAt,
        Instant updatedAt
) {

    private static final int DEFAULT_MAX_ROUNDS = 256;

    /**
     * 创建新的目标快照。
     * <p>
     * 执行流程：
     * 1. 校验会话与目标标识；
     * 2. 读取请求字段并补齐默认值；
     * 3. 返回 revision=1 的激活目标。
     */
    public static GoalAggregate create(String sessionId, GoalCommandVO command, Instant now, long previousRevision) {
        Objects.requireNonNull(now, "now");
        validateSession(sessionId);
        GoalCommandVO normalized = requireCommand(command, GoalAction.CREATE);
        String goalId = normalizeGoalId(sessionId, normalized.goalId());
        String title = requireText(normalized.title(), "title");
        String description = trimToNull(normalized.description());
        int maxRounds = resolveMaxRounds(normalized.maxRounds());
        boolean continuationEnabled = normalized.continuationEnabled() == null || normalized.continuationEnabled();
        return new GoalAggregate(
                sessionId,
                goalId,
                Math.max(previousRevision + 1, 1),
                title,
                description,
                GoalStatus.ACTIVE,
                null,
                maxRounds,
                0,
                continuationEnabled,
                now,
                now
        );
    }

    /**
     * 按动作推进目标状态。
     * <p>
     * 执行流程：
     * 1. 先校验当前状态是否允许动作；
     * 2. 再按动作复制出新快照；
     * 3. revision 与更新时间同步递增。
     */
    public GoalAggregate transition(GoalCommandVO command, Instant now) {
        Objects.requireNonNull(now, "now");
        GoalCommandVO normalized = requireCommand(command, null);
        GoalAction action = normalized.action();
        if (action == GoalAction.UPDATE && status.isTerminal()) {
            throw new IllegalStateException("goal already finished: " + status);
        }
        return switch (action) {
            case UPDATE -> new GoalAggregate(
                    sessionId,
                    goalId,
                    revision + 1,
                    normalized.title() != null ? requireText(normalized.title(), "title") : title,
                    normalized.description() != null ? trimToNull(normalized.description()) : description,
                    status,
                    normalized.blockedReason() != null ? trimToNull(normalized.blockedReason()) : blockedReason,
                    normalized.maxRounds() != null ? resolveMaxRounds(normalized.maxRounds()) : maxRounds,
                    roundsStarted,
                    normalized.continuationEnabled() != null ? normalized.continuationEnabled() : continuationEnabled,
                    createdAt,
                    now
            );
            case PAUSE -> requireStatus(GoalStatus.ACTIVE)
                    .withStatus(GoalStatus.PAUSED, null, now);
            case RESUME -> {
                if (roundsStarted >= maxRounds) {
                    throw new IllegalStateException("goal rounds reached limit: " + maxRounds);
                }
                if (status != GoalStatus.PAUSED && status != GoalStatus.BLOCKED) {
                    throw new IllegalStateException("goal can only resume from paused or blocked state");
                }
                yield new GoalAggregate(
                        sessionId,
                        goalId,
                        revision + 1,
                        title,
                        description,
                        GoalStatus.ACTIVE,
                        null,
                        maxRounds,
                        roundsStarted + 1,
                        true,
                        createdAt,
                        now
                );
            }
            case COMPLETE -> requireStatus(GoalStatus.ACTIVE, GoalStatus.PAUSED, GoalStatus.BLOCKED)
                    .withStatus(GoalStatus.COMPLETED, null, now);
            case BLOCK -> {
                String reason = requireText(normalized.blockedReason(), "blockedReason");
                yield requireStatus(GoalStatus.ACTIVE, GoalStatus.PAUSED)
                        .withStatus(GoalStatus.BLOCKED, reason, now);
            }
            case CLEAR -> new GoalAggregate(
                    sessionId,
                    goalId,
                    revision + 1,
                    title,
                    description,
                    GoalStatus.CLEARED,
                    null,
                    maxRounds,
                    roundsStarted,
                    false,
                    createdAt,
                    now
            );
            case CREATE -> throw new IllegalStateException("goal already exists; use update instead");
        };
    }

    /**
     * 转成快照对象。
     * <p>
     * 执行流程：
     * 1. 读取聚合当前字段；
     * 2. 组装为只读 VO；
     * 3. 供仓储和 API 层直接使用。
     */
    public GoalSnapshotVO toSnapshot() {
        return new GoalSnapshotVO(
                sessionId,
                goalId,
                revision,
                title,
                description,
                status,
                blockedReason,
                maxRounds,
                roundsStarted,
                continuationEnabled,
                createdAt,
                updatedAt
        );
    }

    /**
     * 校验必需条件；不满足时抛出异常。
     */
    private GoalAggregate requireStatus(GoalStatus... allowed) {
        for (GoalStatus candidate : allowed) {
            if (status == candidate) {
                return this;
            }
        }
        throw new IllegalStateException("goal state does not allow action in status: " + status);
    }

    /**
     * 处理with Status。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param nextStatus next Status参数。
     * @param nextBlockedReason next Blocked Reason参数。
     * @param now now参数。
     * @return 方法执行结果。
     */
    private GoalAggregate withStatus(GoalStatus nextStatus, String nextBlockedReason, Instant now) {
        return new GoalAggregate(
                sessionId,
                goalId,
                revision + 1,
                title,
                description,
                nextStatus,
                nextBlockedReason,
                maxRounds,
                roundsStarted,
                continuationEnabled,
                createdAt,
                now
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
     * 校验必需条件；不满足时抛出异常。
     */
    private static GoalCommandVO requireCommand(GoalCommandVO command, GoalAction requiredAction) {
        Objects.requireNonNull(command, "command");
        if (command.action() == null) {
            throw new IllegalArgumentException("goal action must not be blank");
        }
        if (requiredAction != null && command.action() != requiredAction) {
            throw new IllegalArgumentException("goal action must be " + requiredAction);
        }
        return command;
    }

    /**
     * 规范化 目标 ID。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @param goalId 目标 ID。
     * @return 方法执行结果。
     */
    private static String normalizeGoalId(String sessionId, String goalId) {
        String value = trimToNull(goalId);
        if (value != null) {
            return value;
        }
        return "goal-" + sessionId.toLowerCase(Locale.ROOT);
    }

    /**
     * 解析e Max Rounds。
     */
    private static int resolveMaxRounds(Integer maxRounds) {
        if (maxRounds == null) {
            return DEFAULT_MAX_ROUNDS;
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be positive");
        }
        return maxRounds;
    }

    /**
     * 校验必需条件；不满足时抛出异常。
     */
    private static String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
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
