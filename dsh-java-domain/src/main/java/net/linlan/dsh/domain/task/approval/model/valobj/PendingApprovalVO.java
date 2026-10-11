package net.linlan.dsh.domain.task.approval.model.valobj;

import java.time.Instant;

/**
 * PendingApprovalVO 表示PendingApproval的领域值对象。
 */
public record PendingApprovalVO(
        String sessionId,
        String title,
        String preview,
        String status,
        Instant createdAt
) {
}
