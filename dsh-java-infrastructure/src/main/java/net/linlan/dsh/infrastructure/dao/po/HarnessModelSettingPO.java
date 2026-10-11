package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * 保存一条持久化的模型渠道记录。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record HarnessModelSettingPO(
        String channelCode,
        String displayName,
        String providerCode,
        String modelCode,
        String baseUrl,
        String apiKeyRef,
        String protocol,
        boolean enabled,
        boolean active,
        Instant updatedAt
) {
}
