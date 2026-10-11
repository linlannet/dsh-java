package net.linlan.dsh.domain.session.event.model.valobj;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;

/**
 * AssistantMessagePayload 表示AssistantMessagePayload消息。
 */
public record AssistantMessagePayload(
        long turn,
        long step,
        Message message,
        TokenUsage usage
) {}
