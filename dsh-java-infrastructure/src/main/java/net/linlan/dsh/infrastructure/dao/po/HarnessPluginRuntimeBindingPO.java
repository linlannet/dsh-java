package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

/**
 * 保存某插件最近一次桥接解析结果。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record HarnessPluginRuntimeBindingPO(
        String pluginId,
        String packageType,
        String manifestPath,
        String workingDirectory,
        String runtimeCommand,
        boolean bridgeReady,
        String message,
        Instant checkedAt
) {
}
