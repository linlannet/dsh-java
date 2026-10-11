package net.linlan.dsh.domain.agent.contract.outbound;

import net.linlan.dsh.domain.model.entity.Message;

/**
 * InboxNotifications 表示收件箱变更通知的领域契约。
 */
public interface InboxNotificationPublisher {

    /**
     * 处理inserted。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @return 领域处理结果。
     */
    void inserted(Message message);

    /**
     * 处理discarded。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @return 领域处理结果。
     */
    void discarded(Message message);

    /**
     * 处理claimed。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @return 领域处理结果。
     */
    void claimed(Message message, int turn);
}
