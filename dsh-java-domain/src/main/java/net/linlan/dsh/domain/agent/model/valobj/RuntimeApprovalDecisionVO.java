package net.linlan.dsh.domain.agent.model.valobj;

/**
 * RuntimeApprovalDecisionVO 表示运行期审批决策的领域值对象。
 * <p>枚举值覆盖单次放行、会话内放行、拒绝和取消。</p>
 */
public enum RuntimeApprovalDecisionVO {
    ALLOW_ONCE,
    ALLOW_SESSION,
    DENY,
    CANCEL
}
