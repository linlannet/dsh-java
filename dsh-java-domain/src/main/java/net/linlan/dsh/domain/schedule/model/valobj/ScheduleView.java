package net.linlan.dsh.domain.schedule.model.valobj;

import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;

import java.time.Instant;

/**
 * 计划任务视图。
 *
 * @param record 任务记录
 * @param state 视图状态
 * @param deliveryMode 投递模式
 * @param nextAt 下一次触发时刻，可空
 */
public record ScheduleView(ScheduleRecord record, ScheduleState state, String deliveryMode, Instant nextAt) {

    /**
     * 由记录与当前时刻计算视图。
     *
     * @param record 任务记录
     * @param now 当前时刻
     * @return 视图
     */
    public static ScheduleView of(ScheduleRecord record, Instant now) {
        ScheduleState state;
        if (record.ended()) {
            state = ScheduleState.ENDED;
        } else if (record.paused()) {
            state = ScheduleState.PAUSED;
        } else if (record.nextAt() != null && !record.nextAt().isAfter(now)) {
            state = ScheduleState.OVERDUE;
        } else {
            state = ScheduleState.SCHEDULED;
        }
        return new ScheduleView(record, state, record.target().deliveryMode(), record.nextAt());
    }
}
