package net.linlan.dsh.domain.sdk.model.valobj;

import java.time.Instant;

/**
 * SessionSummary 是会话列表项的摘要信息。
 */
public record SessionSummary(
        String sessionId,
        String status,
        Instant createdAt
) {}
