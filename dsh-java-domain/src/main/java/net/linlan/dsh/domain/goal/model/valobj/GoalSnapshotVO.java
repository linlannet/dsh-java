package net.linlan.dsh.domain.goal.model.valobj;

import java.time.Instant;

/**
 * 目标当前快照。
 * <p>
 * 执行流程：
 * 1. 聚合状态变化后生成快照；
 * 2. API 层映射为响应对象；
 * 3. 前端展示当前目标状态。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record GoalSnapshotVO(
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
}
