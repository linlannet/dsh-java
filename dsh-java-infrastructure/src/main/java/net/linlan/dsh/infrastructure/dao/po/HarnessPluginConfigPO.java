package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * 保存一条分层插件配置。
 *
 *
 */
public record HarnessPluginConfigPO(
        String pluginId,
        String configKey,
        String configValue,
        String configLayer,
        Instant updatedAt
) {
}
