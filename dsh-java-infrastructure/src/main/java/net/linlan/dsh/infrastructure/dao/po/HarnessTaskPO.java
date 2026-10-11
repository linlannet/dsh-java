package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;
import java.util.List;

/**
 * 保存一条持久化的 Harness 任务记录。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public record HarnessTaskPO(
        String taskId,
        String sessionId,
        String prompt,
        String enabledToolsJson,
        String status,
        Instant submittedAt
) {
}
