package net.linlan.dsh.api.dto;

import java.time.Instant;

/**
 * 目标展示响应。
 * <p>
 * 执行流程：
 * 1. API 层读取领域快照；
 * 2. 组装为前端可展示对象；
 * 3. 返回当前目标状态。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record GoalViewResponseDTO(
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
        Instant updatedAt,
        boolean exists
) {

    /**
     * 构建一个空响应。
     * <p>
     * 执行流程：
     * 1. 读取 sessionId；
     * 2. 其余字段保持为空或零值；
     * 3. 标记当前没有目标。
     */
    public static GoalViewResponseDTO empty(String sessionId) {
        return new GoalViewResponseDTO(sessionId, null, 0, null, null, null, null, 0, 0, false, null, null, false);
    }
}
