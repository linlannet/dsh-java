package net.linlan.dsh.domain.goal.adapter.repository;

import net.linlan.dsh.domain.goal.model.entity.GoalAggregate;
import java.util.List;
import java.util.Optional;

/**
 * 目标聚合仓储接口。
 * <p>
 * 执行流程：
 * 1. 领域服务读取当前会话的目标快照；
 * 2. 基础设施层完成持久化读写；
 * 3. 返回聚合或完成写入。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IGoalRepository {

    /**
     * 按会话编号查询当前目标。
     * <p>
     * 执行流程：
     * 1. 接收 sessionId；
     * 2. 仓储读取目标快照；
     * 3. 返回可选聚合。
     */
    Optional<GoalAggregate> findBySessionId(String sessionId);

    /**
     * 保存目标聚合。
     * <p>
     * 执行流程：
     * 1. 接收领域聚合；
     * 2. 映射为持久化对象；
     * 3. 写入数据库。
     */
    void save(GoalAggregate aggregate);

    /**
     * 查询全部目标快照。
     * <p>
     * 执行流程：
     * 1. 从持久层读取所有记录；
     * 2. 转换为领域聚合；
     * 3. 返回有序列表。
     */
    List<GoalAggregate> queryAll();
}
