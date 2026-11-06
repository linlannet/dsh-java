package net.linlan.dsh.domain.schedule.model.valobj;

/**
 * 创建计划任务的请求（对应参考源 {@code schedule_create} 参数）。
 *
 * @param title 任务标题
 * @param prompt 触发时投递的提示词
 * @param rrule RRULE 时间规则
 * @param target 投递目标
 * @param sourceSessionId 创建者会话 ID
 */
public record ScheduleCreateRequest(
        String title,
        String prompt,
        String rrule,
        ScheduleTarget target,
        String sourceSessionId
) {

    public ScheduleCreateRequest {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        if (rrule == null || rrule.isBlank()) {
            throw new IllegalArgumentException("rrule is required");
        }
        if (target == null) {
            throw new IllegalArgumentException("target is required: this_session | new_once | new");
        }
    }
}
