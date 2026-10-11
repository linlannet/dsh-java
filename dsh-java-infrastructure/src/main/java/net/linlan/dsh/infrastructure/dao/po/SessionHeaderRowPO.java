package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * 会话头的持久化记录。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record SessionHeaderRowPO(
        String sessionId,
        int version,
        Instant createdAt,
        String cwd,
        String parentSession,
        int seedLength,
        String origin,
        int delegationDepth,
        String agentPreset
) {}
