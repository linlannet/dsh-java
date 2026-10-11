package net.linlan.dsh.domain.goal.model.valobj;

/**
 * 目标变更命令值对象。
 * <p>
 * 执行流程：
 * 1. API 层把请求体转换为命令；
 * 2. 领域服务执行参数校验；
 * 3. 聚合完成状态切换。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record GoalCommandVO(
        GoalAction action,
        String goalId,
        String title,
        String description,
        String blockedReason,
        Integer maxRounds,
        Boolean continuationEnabled
) {
}
