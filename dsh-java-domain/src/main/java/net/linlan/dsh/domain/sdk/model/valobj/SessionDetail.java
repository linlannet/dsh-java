package net.linlan.dsh.domain.sdk.model.valobj;

import java.time.Instant;
import java.util.List;

/**
 * SessionDetail 是会话的完整详情。
 */
public record SessionDetail(
        String sessionId,
        String status,
        Instant createdAt,
        List<Object> events
) {}
