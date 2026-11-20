package net.linlan.dsh.domain.session.log.adapter.repository;

import net.linlan.dsh.domain.session.log.model.entity.SessionEventEntity;
import java.util.List;

/**
 * Defines durable append-only access for session events.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public interface ISessionEventRepository {

    void append(SessionEventEntity event);

    List<SessionEventEntity> queryBySessionId(String sessionId);
}
