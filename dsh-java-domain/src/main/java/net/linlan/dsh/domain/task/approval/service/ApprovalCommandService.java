package net.linlan.dsh.domain.task.approval.service;

import net.linlan.dsh.domain.session.ISessionLogService;
import net.linlan.dsh.domain.session.registry.adapter.repository.IHarnessSessionRepository;
import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import net.linlan.dsh.domain.task.IApprovalCommandService;
import net.linlan.dsh.domain.task.IHarnessExecutionService;
import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalActionVO;
import net.linlan.dsh.domain.task.execution.model.valobj.HarnessExecutionResultVO;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * ApprovalCommandService 封装ApprovalCommand命令的领域规则与流程。
 */
@Service
public class ApprovalCommandService implements IApprovalCommandService {

    private final IHarnessSessionRepository harnessSessionRepository;
    private final ISessionLogService sessionLogService;
    private final IHarnessExecutionService harnessExecutionService;

    /**
     * 初始化审批命令服务。
     * <p>流程：接收批准/拒绝命令 → 更新审批状态 → 通知任务执行。</p>
     */
    public ApprovalCommandService(
            IHarnessSessionRepository harnessSessionRepository,
            ISessionLogService sessionLogService,
            IHarnessExecutionService harnessExecutionService
    ) {
        this.harnessSessionRepository = harnessSessionRepository;
        this.sessionLogService = sessionLogService;
        this.harnessExecutionService = harnessExecutionService;
    }

    @Override
    public ApprovalActionVO approve(String sessionId) {
        HarnessSessionAggregate session = harnessSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found: " + sessionId));
        if (session.getStatus() != HarnessStatusEnumVO.PENDING_APPROVAL) {
            throw new IllegalStateException("session is not pending approval: " + sessionId);
        }
        session.changeStatus(HarnessStatusEnumVO.QUEUED);
        harnessSessionRepository.save(session);
        sessionLogService.recordApprovalAccepted(sessionId);
        HarnessExecutionResultVO executionResult = harnessExecutionService.executeSession(sessionId);
        Instant operatedAt = Instant.now();
        return new ApprovalActionVO(sessionId, executionResult.status().name(), executionResult.assistantMessage(), operatedAt);
    }
}
