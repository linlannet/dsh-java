package net.linlan.dsh.domain.agent.service;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.agent.contract.outbound.InboxNotificationPublisher;

/**
 * LoggingInboxNotifications 表示收件箱日志通知的领域适配实现。
 */
public final class LoggingInboxNotifications implements InboxNotificationPublisher {

    public static final LoggingInboxNotifications INSTANCE = new LoggingInboxNotifications();

    /**
     * claimed。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param message 消息内容或对象。
     * @param turn 当前回合。
     */

    /**
     * claimed。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param message 消息内容或对象。
     * @param turn 当前回合。
     */

    @Override
    public void inserted(Message message) {
        // no-op
    }

    @Override
    public void discarded(Message message) {
        // no-op
    }

    @Override
    public void claimed(Message message, int turn) {
        // no-op
    }
}
