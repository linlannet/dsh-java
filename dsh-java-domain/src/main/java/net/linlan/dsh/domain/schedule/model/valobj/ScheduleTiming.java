package net.linlan.dsh.domain.schedule.model.valobj;

import java.time.Duration;
import java.time.Instant;

/**
 * 计划任务的时间记录（对应参考源 schedule 的带类型 {@code timing}）。
 *
 * <p>职责：把「一次性 / 固定间隔 / RRULE」统一成可计算的时间规则，提供首次与下一次发生时间。</p>
 * <p>示例：{@code ScheduleTiming.rrule("DTSTART;TZID=Asia/Shanghai:20261013T090000\nRRULE:FREQ=DAILY", now)}</p>
 *
 * @param kind 时间类型
 * @param afterSeconds 相对时长（仅 {@link ScheduleKind#AFTER}）
 * @param everySeconds 固定间隔（仅 {@link ScheduleKind#EVERY}）
 * @param rrule RRULE 文本（{@link ScheduleKind#DAILY}、{@link ScheduleKind#WEEKLY}、{@link ScheduleKind#RRULE}）
 * @param anchor 一次性任务的触发时刻，或重复任务的首次发生时刻
 */
public record ScheduleTiming(
        ScheduleKind kind,
        Long afterSeconds,
        Long everySeconds,
        String rrule,
        Instant anchor
) {

    /** 固定间隔任务的最小间隔（秒）。 */
    public static final long MIN_EVERY_INTERVAL_SECONDS = 300;

    public ScheduleTiming {
        if (kind == null) {
            throw new IllegalArgumentException("schedule kind is required");
        }
        if (anchor == null) {
            throw new IllegalArgumentException("schedule anchor is required");
        }
        switch (kind) {
            case AFTER -> {
                if (afterSeconds == null || afterSeconds <= 0) {
                    throw new IllegalArgumentException("afterSeconds must be positive");
                }
            }
            case EVERY -> {
                if (everySeconds == null || everySeconds < MIN_EVERY_INTERVAL_SECONDS) {
                    throw new IllegalArgumentException(
                            "everySeconds must be >= " + MIN_EVERY_INTERVAL_SECONDS);
                }
            }
            case DAILY, WEEKLY, RRULE -> {
                if (rrule == null || rrule.isBlank()) {
                    throw new IllegalArgumentException("rrule is required for " + kind);
                }
            }
            case AT -> {
                // anchor 即触发时刻，无需附加字段
            }
        }
    }

    /**
     * 创建「N 秒后触发一次」的时间记录。
     *
     * @param delay 延迟
     * @param now 当前时刻
     * @return 时间记录
     */
    public static ScheduleTiming after(Duration delay, Instant now) {
        if (delay == null || delay.isNegative() || delay.isZero()) {
            throw new IllegalArgumentException("after delay must be positive");
        }
        return new ScheduleTiming(ScheduleKind.AFTER, delay.getSeconds(), null, null, now.plus(delay));
    }

    /**
     * 创建「指定时刻触发一次」的时间记录。
     *
     * @param target 触发时刻
     * @return 时间记录
     */
    public static ScheduleTiming at(Instant target) {
        if (target == null) {
            throw new IllegalArgumentException("at target is required");
        }
        return new ScheduleTiming(ScheduleKind.AT, null, null, null, target);
    }

    /**
     * 创建固定间隔重复的时间记录。
     *
     * @param interval 间隔
     * @param now 当前时刻
     * @return 时间记录
     */
    public static ScheduleTiming every(Duration interval, Instant now) {
        if (interval == null) {
            throw new IllegalArgumentException("every interval is required");
        }
        if (interval.getSeconds() < MIN_EVERY_INTERVAL_SECONDS) {
            throw new IllegalArgumentException(
                    "every interval must be >= " + MIN_EVERY_INTERVAL_SECONDS + " seconds");
        }
        return new ScheduleTiming(ScheduleKind.EVERY, null, interval.getSeconds(), null, now.plus(interval));
    }

    /**
     * 由 RRULE 文本创建时间记录，并按 FREQ 派生 DAILY / WEEKLY / RRULE 类型。
     *
     * @param rrule RRULE 文本
     * @param now 当前时刻（缺少 DTSTART 时作为起点）
     * @return 时间记录
     */
    public static ScheduleTiming rrule(String rrule, Instant now) {
        RRule parsed = RRule.parse(rrule, now);
        ScheduleKind kind = switch (parsed.freq()) {
            case "DAILY" -> ScheduleKind.DAILY;
            case "WEEKLY" -> ScheduleKind.WEEKLY;
            default -> ScheduleKind.RRULE;
        };
        return new ScheduleTiming(kind, null, null, rrule, parsed.atOrAfter(now));
    }

    /** 是否重复任务。 */
    public boolean recurring() {
        return kind.recurring();
    }

    /**
     * 计算第一个不早于给定时刻的发生时间。
     *
     * @param now 参考时刻
     * @return 发生时间
     */
    public Instant firstAtOrAfter(Instant now) {
        return switch (kind) {
            case AFTER, AT -> anchor;
            case EVERY -> {
                Instant next = anchor;
                while (!next.isAfter(now)) {
                    next = next.plusSeconds(everySeconds);
                }
                yield next;
            }
            case DAILY, WEEKLY, RRULE -> RRule.parse(rrule, anchor).atOrAfter(now);
        };
    }

    /**
     * 计算严格晚于给定时刻的下一次发生时间。
     *
     * @param from 参考时刻
     * @return 下一次发生时间；一次性任务返回 null
     */
    public Instant nextAfter(Instant from) {
        if (!recurring()) {
            return null;
        }
        return switch (kind) {
            case EVERY -> {
                Instant next = anchor;
                while (!next.isAfter(from)) {
                    next = next.plusSeconds(everySeconds);
                }
                yield next;
            }
            default -> RRule.parse(rrule, anchor).nextAfter(from);
        };
    }
}
