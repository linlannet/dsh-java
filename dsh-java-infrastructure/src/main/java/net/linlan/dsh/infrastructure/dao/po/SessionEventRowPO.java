package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * 新事件日志表中一条会话事件的持久化记录。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record SessionEventRowPO(
        Long id,
        String sessionId,
        long seq,
        String eventType,
        Instant eventTime,
        String payloadJson,
        String surfaceOp,
        String sourceEventSeqs
) {
    /**
     * 创建会话事件行持久化对象。
     * <p>用途：承载事件表中的一条领域事件记录。</p>
     */
    public SessionEventRowPO(
            String sessionId, long seq, String eventType, Instant eventTime,
            String payloadJson, String surfaceOp, String sourceEventSeqs
    ) {
        this(null, sessionId, seq, eventType, eventTime, payloadJson, surfaceOp, sourceEventSeqs);
    }
}
