package net.linlan.dsh.domain.schedule.model.entity;

import net.linlan.dsh.domain.schedule.model.valobj.ScheduleTarget;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleTiming;

import java.time.Instant;
import java.util.Objects;

/**
 * 计划任务实体（宿主范围，按 id 寻址，不绑定创建会话）。
 *
 * <p>参考源把「每个会话只看自己的提醒」升级为「宿主全部任务」：{@code sourceSessionId} 只记录创建者，
 * 读取与编辑按 id 进行；{@code target} 决定触发后的投递目的地。</p>
 *
 * @param id 任务 ID
 * @param title 任务标题
 * @param prompt 触发时投递的提示词
 * @param timing 时间规则
 * @param target 投递目标
 * @param targetSessionId 已确定的投递会话（{@code this_session} 时等于创建会话），可空
 * @param sourceSessionId 创建者会话 ID
 * @param paused 是否暂停
 * @param ended 是否已结束（一次性任务触发后为 true）
 * @param createdAt 创建时刻
 * @param updatedAt 最后变更时刻
 * @param lastRunAt 最近触发时刻，可空
 * @param nextAt 下一次触发时刻，已结束或暂停时可为空
 */
public record ScheduleRecord(
        String id,
        String title,
        String prompt,
        ScheduleTiming timing,
        ScheduleTarget target,
        String targetSessionId,
        String sourceSessionId,
        boolean paused,
        boolean ended,
        Instant createdAt,
        Instant updatedAt,
        Instant lastRunAt,
        Instant nextAt
) {

    public ScheduleRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("schedule id must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("schedule title must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("schedule prompt must not be blank");
        }
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * 任务是否处于活动状态（未暂停且未结束）。
     *
     * @return 活动返回 true
     */
    public boolean active() {
        return !paused && !ended;
    }

    /**
     * 生成「已触发」的新快照：记录最近触发时刻与下一次触发时刻。
     *
     * @param firedAt 本次触发时刻
     * @param next 下一次触发时刻；一次性任务传 null
     * @param now 当前时刻，用作 updatedAt
     * @return 新快照
     */
    public ScheduleRecord fired(Instant firedAt, Instant next, Instant now) {
        return new ScheduleRecord(id, title, prompt, timing, target, targetSessionId, sourceSessionId,
                false, next == null, createdAt, now, firedAt, next);
    }

    /**
     * 复制并替换可变字段。
     *
     * @param newTitle 新标题，null 表示不变
     * @param newPrompt 新提示词，null 表示不变
     * @param newTiming 新时间规则，null 表示不变
     * @param newTarget 新投递目标，null 表示不变
     * @param newTargetSessionId 新投递会话
     * @param newPaused 新暂停状态，null 表示不变
     * @param newNextAt 新下一次触发时刻
     * @param now 变更时刻
     * @return 新快照
     */
    public ScheduleRecord patched(String newTitle, String newPrompt, ScheduleTiming newTiming,
                                  ScheduleTarget newTarget, String newTargetSessionId,
                                  Boolean newPaused, Instant newNextAt, Instant now) {
        return new ScheduleRecord(
                id,
                newTitle != null ? newTitle : title,
                newPrompt != null ? newPrompt : prompt,
                newTiming != null ? newTiming : timing,
                newTarget != null ? newTarget : target,
                newTargetSessionId,
                sourceSessionId,
                newPaused != null ? newPaused : paused,
                ended,
                createdAt,
                now,
                lastRunAt,
                newNextAt);
    }
}
