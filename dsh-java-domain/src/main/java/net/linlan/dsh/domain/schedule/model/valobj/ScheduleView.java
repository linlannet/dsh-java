package net.linlan.dsh.domain.schedule.model.valobj;

import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import java.time.Instant;

/**
 * ScheduleView 领域值对象。
 * <p>职责：承载领域规则、状态或边界，供上层编排与基础设施适配。</p>
 */
public record ScheduleView(ScheduleRecord record, ScheduleState state, String deliveryMode) {

    /**
     * 创建目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param record record参数。
     * @param now now参数。
     * @return 创建的领域对象。
     */
    public static ScheduleView of(ScheduleRecord record, Instant now) {
        ScheduleState state = record.scheduledAt().isAfter(now)
                ? ScheduleState.SCHEDULED
                : ScheduleState.OVERDUE;
        return new ScheduleView(record, state, "session-local");
    }
}
