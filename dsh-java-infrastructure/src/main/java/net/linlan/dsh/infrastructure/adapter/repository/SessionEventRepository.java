package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.session.log.adapter.repository.ISessionEventRepository;
import net.linlan.dsh.domain.session.log.model.entity.SessionEventEntity;
import net.linlan.dsh.infrastructure.dao.ISessionEventDao;
import net.linlan.dsh.infrastructure.dao.po.SessionEventPO;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通过 DAO 层持久化追加写的会话事件。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class SessionEventRepository implements ISessionEventRepository {

    private final ISessionEventDao sessionEventDao;

    /**
     * 处理会话 事件 Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionEventDao 会话 事件 Dao参数。
     * @return 方法执行结果。
     */
    public SessionEventRepository(ISessionEventDao sessionEventDao) {
        this.sessionEventDao = sessionEventDao;
    }

    @Override
    @Transactional
    public void append(SessionEventEntity event) {
        sessionEventDao.append(new SessionEventPO(
                event.sessionId(),
                event.eventType(),
                event.payload(),
                event.occurredAt()
        ));
    }
    /**
     * 查询By Session Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public List<SessionEventEntity> queryBySessionId(String sessionId) {
        return sessionEventDao.queryBySessionId(sessionId).stream()
                .map(eventPO -> new SessionEventEntity(
                        eventPO.sessionId(),
                        eventPO.eventType(),
                        eventPO.payload(),
                        eventPO.occurredAt()
                ))
                .toList();
    }
}
