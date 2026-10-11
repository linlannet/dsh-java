package net.linlan.dsh.domain.agent.model.valobj;

/**
 * AgentCancelCause 表示智能体取消原因的领域值对象。
 */
public sealed interface AgentCancelCause
        permits AgentCancelCause.UserCancel,
                AgentCancelCause.Disposed,
                AgentCancelCause.Maintenance {

    /**
     * UserCancel 表示UserCancel。
     */
    record UserCancel(String reason) implements AgentCancelCause {}

    /**
     * Disposed 表示Disposed。
     */
    record Disposed() implements AgentCancelCause {}

    /**
     * Maintenance 表示Maintenance。
     */
    record Maintenance() implements AgentCancelCause {}
}
