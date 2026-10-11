package net.linlan.dsh.domain.session.registry.adapter.repository;

import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import java.util.List;
import java.util.Optional;

/**
 * Defines durable access for session aggregates.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
public interface IHarnessSessionRepository {

    void save(HarnessSessionAggregate session);

    Optional<HarnessSessionAggregate> findBySessionId(String sessionId);

    List<HarnessSessionAggregate> listRecent(int limit);
}
