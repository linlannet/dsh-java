package net.linlan.dsh.domain.schedule.model.exception;

import java.time.Instant;

/**
 * 计划任务乐观并发冲突（对应参考源 {@code schedule_conflict}）。
 */
public class ScheduleConflictException extends RuntimeException {

    private final String scheduleId;
    private final Instant expected;
    private final Instant actual;

    /**
     * 构造异常。
     *
     * @param scheduleId 任务 ID
     * @param expected 调用方期望的版本时间戳
     * @param actual 当前版本时间戳
     */
    public ScheduleConflictException(String scheduleId, Instant expected, Instant actual) {
        super("schedule_conflict: " + scheduleId + ", expected=" + expected + ", actual=" + actual);
        this.scheduleId = scheduleId;
        this.expected = expected;
        this.actual = actual;
    }

    /** 任务 ID。 */
    public String scheduleId() {
        return scheduleId;
    }

    /** 期望版本。 */
    public Instant expected() {
        return expected;
    }

    /** 当前版本。 */
    public Instant actual() {
        return actual;
    }
}
