package net.linlan.dsh.domain.schedule.model.valobj;

/**
 * 计划任务的变更补丁，字段为 null 表示不修改。
 *
 * @param title 新标题
 * @param prompt 新提示词
 * @param rrule 新 RRULE 规则
 * @param target 新投递目标
 * @param paused 新暂停状态
 */
public record SchedulePatch(
        String title,
        String prompt,
        String rrule,
        ScheduleTarget target,
        Boolean paused
) {

    /**
     * 补丁是否为空。
     *
     * @return 无任何字段时返回 true
     */
    public boolean isEmpty() {
        return title == null && prompt == null && rrule == null && target == null && paused == null;
    }
}
