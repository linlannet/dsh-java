package net.linlan.dsh.domain.task.queue.service;

import net.linlan.dsh.domain.task.IHarnessTaskQueueService;
import net.linlan.dsh.domain.task.execution.model.valobj.RuntimeExecutionPlanVO;
import net.linlan.dsh.domain.task.queue.adapter.repository.IHarnessTaskQueueRepository;
import net.linlan.dsh.domain.task.queue.model.entity.HarnessTaskEntity;
import net.linlan.dsh.domain.task.queue.model.valobj.QueuedTaskResultVO;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 排队任务服务。
 * <p>
 * 执行流程：
 * 1. 接收任务提交后的执行计划；
 * 2. 生成任务编号并组装排队实体；
 * 3. 保存后返回排队结果。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
@Service
public class HarnessTaskQueueService implements IHarnessTaskQueueService {

    private final IHarnessTaskQueueRepository harnessTaskQueueRepository;

    /**
     * 处理Harness 任务 Queue Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param harnessTaskQueueRepository harness 任务 Queue Repository参数。
     * @return 方法执行结果。
     */
    public HarnessTaskQueueService(IHarnessTaskQueueRepository harnessTaskQueueRepository) {
        this.harnessTaskQueueRepository = harnessTaskQueueRepository;
    }

    /**
     * 创建一个排队任务。
     * <p>
     * 执行流程：
     * 1. 生成 taskId；
     * 2. 封装任务实体并写入仓储；
     * 3. 返回提交结果。
     */
    @Override
    public QueuedTaskResultVO createQueuedTask(String sessionId, RuntimeExecutionPlanVO plan) {
        String taskId = UUID.randomUUID().toString();
        HarnessTaskEntity task = new HarnessTaskEntity(taskId, sessionId, plan.prompt(), plan.enabledTools(), plan.status(), plan.acceptedAt());
        harnessTaskQueueRepository.save(task);
        return new QueuedTaskResultVO(taskId, sessionId, plan.status(), plan.enabledTools(), plan.acceptedAt());
    }
}
