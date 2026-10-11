package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.session.registry.adapter.repository.IHarnessSessionRepository;
import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import net.linlan.dsh.infrastructure.dao.IHarnessSessionDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessSessionPO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通过 DAO 和缓存层持久化会话。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class HarnessSessionRepository implements IHarnessSessionRepository {

    private final IHarnessSessionDao harnessSessionDao;

    /**
     * 处理Harness 会话 Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param harnessSessionDao harness 会话 Dao参数。
     * @return 方法执行结果。
     */
    public HarnessSessionRepository(IHarnessSessionDao harnessSessionDao) {
        this.harnessSessionDao = harnessSessionDao;
    }

    @Override
    @Transactional
    public void save(HarnessSessionAggregate session) {
        HarnessSessionPO sessionPO = new HarnessSessionPO(
                session.getSessionId(),
                session.getWorkspaceId(),
                session.getProfileCode(),
                session.getExecutionMode(),
                session.getResolvedChannelCode(),
                session.getResolvedModel(),
                session.getStatus().name(),
                session.getCreatedAt()
        );
        harnessSessionDao.save(sessionPO);
    }
    /**
     * 查找By Session Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public Optional<HarnessSessionAggregate> findBySessionId(String sessionId) {
        return harnessSessionDao.findBySessionId(sessionId).map(this::toAggregate);
    }
    /**
     * 列出Recent。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param limit limit参数。
     * @return 方法执行结果。
     */


    @Override
    public List<HarnessSessionAggregate> listRecent(int limit) {
        return harnessSessionDao.listRecent(limit).stream().map(this::toAggregate).toList();
    }

    /**
     * 转换为 Aggregate。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sessionPO 会话 P O参数。
     * @return 转换后的目标对象。
     */
    private HarnessSessionAggregate toAggregate(HarnessSessionPO sessionPO) {
        HarnessSessionAggregate session = new HarnessSessionAggregate(
                sessionPO.sessionId(),
                sessionPO.workspaceId(),
                sessionPO.profileCode(),
                sessionPO.createdAt()
        );
        session.prepare(sessionPO.executionMode(), sessionPO.resolvedChannelCode(), sessionPO.resolvedModel());
        session.changeStatus(HarnessStatusEnumVO.valueOf(sessionPO.status()));
        return session;
    }
}
