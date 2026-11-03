package net.linlan.dsh.domain.schedule.model.valobj;

/**
 * 计划任务的时间类型。
 *
 * <p>对应参考源 schedule 领域的 {@code After / At / Every / Daily / Weekly / RRule}；
 * Cron 表达式统一由 {@link RRULE} 表达（5 字段 cron 可由 RRULE 等价改写）。</p>
 */
public enum ScheduleKind {
    /** 创建后经过固定时长触发一次。 */
    AFTER,
    /** 指定时刻触发一次。 */
    AT,
    /** 固定间隔重复触发。 */
    EVERY,
    /** RRULE 中 FREQ=DAILY 的日历任务。 */
    DAILY,
    /** RRULE 中 FREQ=WEEKLY 的日历任务。 */
    WEEKLY,
    /** 其它 RRULE 规则。 */
    RRULE;

    /**
     * 是否为重复任务。
     *
     * @return 重复任务返回 true
     */
    public boolean recurring() {
        return this != AFTER && this != AT;
    }
}
