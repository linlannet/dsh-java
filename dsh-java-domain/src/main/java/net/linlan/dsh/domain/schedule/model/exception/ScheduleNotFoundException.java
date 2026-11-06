package net.linlan.dsh.domain.schedule.model.exception;

/**
 * 计划任务不存在。
 */
public class ScheduleNotFoundException extends RuntimeException {

    private final String scheduleId;

    /**
     * 构造异常。
     *
     * @param scheduleId 任务 ID
     */
    public ScheduleNotFoundException(String scheduleId) {
        super("schedule not found: " + scheduleId);
        this.scheduleId = scheduleId;
    }

    /** 任务 ID。 */
    public String scheduleId() {
        return scheduleId;
    }
}
