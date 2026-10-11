package net.linlan.dsh.domain.session.conversation.model.valobj;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents one chat-visible conversation message.
 *
 * @param role message role such as user or assistant
 * @param content message content
 * @param occurredAt message timestamp
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public record ConversationMessageVO(
        String role,
        String content,
        List<Map<String, Object>> blocks,
        Instant occurredAt
) {
    public ConversationMessageVO(String role, String content, Instant occurredAt) {
        this(role, content, List.of(), occurredAt);
    }
}
