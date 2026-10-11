package net.linlan.dsh.domain.task.queue.adapter.repository;

import net.linlan.dsh.domain.task.queue.model.entity.HarnessTaskEntity;
import java.util.Optional;

/**
 * 排队任务仓储接口。
 * <p>
 * 执行流程：
 * 1. 领域层完成任务入队；
 * 2. 仓储负责保存或读取排队任务；
 * 3. 返回持久化后的任务实体。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IHarnessTaskQueueRepository {

    /**
     * 保存排队任务。
     * <p>
     * 执行流程：
     * 1. 接收任务实体；
     * 2. 落库或更新状态；
     * 3. 完成写入。
     */
    void save(HarnessTaskEntity task);

    /**
     * 根据任务编号查询排队任务。
     * <p>
     * 执行流程：
     * 1. 读取 taskId；
     * 2. 查询持久化记录；
     * 3. 返回可选实体。
     */
    Optional<HarnessTaskEntity> findByTaskId(String taskId);
}
