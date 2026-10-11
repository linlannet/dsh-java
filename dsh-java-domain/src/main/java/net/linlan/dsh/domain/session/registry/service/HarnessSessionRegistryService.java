package net.linlan.dsh.domain.session.registry.service;

import net.linlan.dsh.domain.session.IHarnessSessionRegistryService;
import net.linlan.dsh.domain.task.execution.model.valobj.RuntimeExecutionPlanVO;
import net.linlan.dsh.domain.session.registry.adapter.repository.IHarnessSessionRepository;
import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import net.linlan.dsh.domain.session.registry.model.valobj.SessionOpenedResultVO;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Owns session creation and status persistence.
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 *
 */
@Service
public class HarnessSessionRegistryService implements IHarnessSessionRegistryService {

    private final IHarnessSessionRepository harnessSessionRepository;

    /**
     * 处理Harness 会话 Registry Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param harnessSessionRepository harness 会话 Repository参数。
     * @return 方法执行结果。
     */
    public HarnessSessionRegistryService(IHarnessSessionRepository harnessSessionRepository) {
        this.harnessSessionRepository = harnessSessionRepository;
    }

    @Override
    public SessionOpenedResultVO openQueuedSession(RuntimeExecutionPlanVO plan) {
        String sessionId = UUID.randomUUID().toString();
        HarnessSessionAggregate session = new HarnessSessionAggregate(sessionId, plan.workspaceId(), plan.profileCode(), plan.acceptedAt());
        session.prepare(plan.executionMode(), plan.resolvedChannelCode(), plan.resolvedModel());
        session.changeStatus(plan.status());
        harnessSessionRepository.save(session);
        return new SessionOpenedResultVO(
                sessionId, plan.resolvedChannelCode(), plan.resolvedModel(), plan.status(), plan.acceptedAt());
    }
}
