package net.linlan.dsh.domain.session.event.model.valobj;

import net.linlan.dsh.domain.model.entity.Message;

/**
 * UserMessagePayload 表示UserMessagePayload消息。
 */
public record UserMessagePayload(long turn, long step, Message message) {
    public UserMessagePayload {
        if (message != null && !"user".equals(message.role())) {
            throw new IllegalArgumentException("UserMessagePayload requires role=user, got: " + message.role());
        }
    }
}
