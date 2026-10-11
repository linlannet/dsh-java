package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * 持久化目标状态行。
 * <p>
 * 执行流程：
 * 1. 仓储层把聚合拆成 PO；
 * 2. DAO 负责写入数据库；
 * 3. 读取时再映射回领域对象。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record GoalStatePO(
        String sessionId,
        String goalId,
        long revision,
        String title,
        String description,
        String status,
        String blockedReason,
        int maxRounds,
        int roundsStarted,
        boolean continuationEnabled,
        Instant createdAt,
        Instant updatedAt
) {
}
