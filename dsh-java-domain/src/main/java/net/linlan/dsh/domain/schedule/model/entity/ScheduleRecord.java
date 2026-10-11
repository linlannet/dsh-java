package net.linlan.dsh.domain.schedule.model.entity;

import net.linlan.dsh.domain.schedule.model.valobj.ScheduleKind;
import java.time.Instant;

/**
 * ScheduleRecord 领域实体。
 * <p>职责：承载领域规则、状态或边界，供上层编排与基础设施适配。</p>
 */
public record ScheduleRecord(
        String id,
        ScheduleKind kind,
        String prompt,
        Long afterSeconds,
        Long everySeconds,
        Instant scheduledAt,
        String sessionId
) {
    public ScheduleRecord {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
    }
}
