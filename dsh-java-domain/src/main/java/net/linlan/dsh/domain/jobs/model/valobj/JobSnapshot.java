package net.linlan.dsh.domain.jobs.model.valobj;

import net.linlan.dsh.domain.jobs.model.entity.JobRecord;
import java.time.Instant;

/**
 * Read-only projection of a job, safe to hand to listeners and tools.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record JobSnapshot(
        String id,
        String kind,
        String label,
        String ownerSessionId,
        JobStatus status,
        String detail,
        String output,
        Instant startedAt,
        Instant finishedAt
) {
    /**
     * 根据目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param record record参数。
     * @return 创建的领域对象。
     */
    public static JobSnapshot from(JobRecord record) {
        JobOutcome outcome = record.outcome();
        return new JobSnapshot(
                record.id(),
                record.kind().name().toLowerCase(),
                record.label(),
                record.ownerSessionId(),
                record.status(),
                record.detail(),
                outcome != null ? outcome.output() : null,
                record.startedAt(),
                record.finishedAt()
        );
    }
}
