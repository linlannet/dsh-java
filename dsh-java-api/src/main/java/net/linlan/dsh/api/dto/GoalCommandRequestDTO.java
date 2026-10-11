package net.linlan.dsh.api.dto;

/**
 * 目标变更请求。
 * <p>
 * 执行流程：
 * 1. 控制器接收请求体；
 * 2. API 层转换为领域命令；
 * 3. 领域服务执行状态机。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record GoalCommandRequestDTO(
        String action,
        String goalId,
        String title,
        String description,
        String blockedReason,
        Integer maxRounds,
        Boolean continuationEnabled
) {
}
