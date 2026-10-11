package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.goal.adapter.repository.IGoalRepository;
import net.linlan.dsh.domain.goal.model.entity.GoalAggregate;
import net.linlan.dsh.domain.goal.model.valobj.GoalStatus;
import net.linlan.dsh.infrastructure.dao.IGoalStateDao;
import net.linlan.dsh.infrastructure.dao.po.GoalStatePO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 目标仓储实现。
 * <p>
 * 执行流程：
 * 1. 接收领域目标聚合；
 * 2. 映射为持久化对象；
 * 3. 通过 DAO 读写数据库。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class GoalRepository implements IGoalRepository {

    private final IGoalStateDao goalStateDao;

    /**
     * 处理目标 Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param goalStateDao 目标 状态 Dao参数。
     * @return 方法执行结果。
     */
    public GoalRepository(IGoalStateDao goalStateDao) {
        this.goalStateDao = goalStateDao;
    }

    @Override
    public Optional<GoalAggregate> findBySessionId(String sessionId) {
        return goalStateDao.findBySessionId(sessionId).map(this::toAggregate);
    }

    @Override
    @Transactional
    public void save(GoalAggregate aggregate) {
        goalStateDao.save(new GoalStatePO(
                aggregate.sessionId(),
                aggregate.goalId(),
                aggregate.revision(),
                aggregate.title(),
                aggregate.description(),
                aggregate.status().name(),
                aggregate.blockedReason(),
                aggregate.maxRounds(),
                aggregate.roundsStarted(),
                aggregate.continuationEnabled(),
                aggregate.createdAt(),
                aggregate.updatedAt()
        ));
    }
    /**
     * 查询All。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public List<GoalAggregate> queryAll() {
        return goalStateDao.queryAll().stream().map(this::toAggregate).toList();
    }

    /**
     * 转换为 Aggregate。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param po PO参数。
     * @return 转换后的目标对象。
     */
    private GoalAggregate toAggregate(GoalStatePO po) {
        return new GoalAggregate(
                po.sessionId(),
                po.goalId(),
                po.revision(),
                po.title(),
                po.description(),
                GoalStatus.valueOf(po.status()),
                po.blockedReason(),
                po.maxRounds(),
                po.roundsStarted(),
                po.continuationEnabled(),
                po.createdAt(),
                po.updatedAt()
        );
    }
}
