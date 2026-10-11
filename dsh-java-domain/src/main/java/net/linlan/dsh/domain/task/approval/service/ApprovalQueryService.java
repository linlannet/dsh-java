package net.linlan.dsh.domain.task.approval.service;

import net.linlan.dsh.domain.session.IConversationQueryService;
import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import net.linlan.dsh.domain.task.IApprovalQueryService;
import net.linlan.dsh.domain.task.approval.model.valobj.PendingApprovalVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * ApprovalQueryService 封装ApprovalQuery的领域规则与流程。
 */
@Service
public class ApprovalQueryService implements IApprovalQueryService {

    private final IConversationQueryService conversationQueryService;

    /**
     * 处理审批 Query Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param conversationQueryService conversation Query Service参数。
     * @return 方法执行结果。
     */
    public ApprovalQueryService(IConversationQueryService conversationQueryService) {
        this.conversationQueryService = conversationQueryService;
    }

    @Override
    public List<PendingApprovalVO> listPendingApprovals(int limit) {
        return conversationQueryService.listRecentSessions(limit, 0).stream()
                .filter(session -> HarnessStatusEnumVO.PENDING_APPROVAL.name().equals(session.status()))
                .map(session -> new PendingApprovalVO(
                        session.sessionId(),
                        session.title(),
                        session.preview(),
                        session.status(),
                        session.createdAt()
                ))
                .toList();
    }
}
