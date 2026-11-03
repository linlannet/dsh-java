package net.linlan.dsh.domain.schedule.model.valobj;

/**
 * 计划任务在视图上的状态。
 *
 * <p>对应参考源的 {@code scheduled / overdue / paused / ended}。</p>
 */
public enum ScheduleState {
    /** 未到期。 */
    SCHEDULED,
    /** 已到期但尚未派发。 */
    OVERDUE,
    /** 已暂停。 */
    PAUSED,
    /** 已结束（一次性任务触发完成）。 */
    ENDED,
    /** 已派发（历史状态，保留兼容）。 */
    DISPATCHED
}
