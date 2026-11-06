package net.linlan.dsh.domain.schedule.model.valobj;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 最小 RRULE 解析与展开（对应 TS 侧 schedule 的 {@code RRULE} 时间规则）。
 *
 * <p>支持一行 {@code DTSTART[;TZID=...]:yyyyMMdd'T'HHmmss} 加一行 {@code RRULE:FREQ=...}。
 * 支持的 FREQ：{@code MINUTELY}、{@code HOURLY}、{@code DAILY}、{@code WEEKLY}、{@code MONTHLY}；
 * 支持的其它部件：{@code INTERVAL}、{@code BYDAY}、{@code BYHOUR}、{@code BYMINUTE}。
 * {@code COUNT}、{@code UNTIL} 等未实现部件会显式拒绝，避免静默产生错误时间规则。</p>
 *
 * <p>示例：{@code DTSTART;TZID=Asia/Shanghai:20261013T090000\nRRULE:FREQ=DAILY}</p>
 */
public final class RRule {

    private static final int MAX_PERIODS = 10000;
    private static final Set<String> SUPPORTED_FREQ =
            Set.of("MINUTELY", "HOURLY", "DAILY", "WEEKLY", "MONTHLY");

    private final String raw;
    private final Instant start;
    private final ZoneId zone;
    private final String freq;
    private final int interval;
    private final Set<DayOfWeek> byDay;
    private final Integer byHour;
    private final Integer byMinute;

    private RRule(String raw, Instant start, ZoneId zone, String freq, int interval,
                  Set<DayOfWeek> byDay, Integer byHour, Integer byMinute) {
        this.raw = raw;
        this.start = start;
        this.zone = zone;
        this.freq = freq;
        this.interval = interval;
        this.byDay = byDay;
        this.byHour = byHour;
        this.byMinute = byMinute;
    }

    /**
     * 解析 RRULE 文本。
     *
     * @param text RRULE 文本（可含 DTSTART 行）
     * @param fallbackStart 未提供 DTSTART 时使用的起点
     * @return 解析结果
     */
    public static RRule parse(String text, Instant fallbackStart) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("rrule must not be blank");
        }
        ZoneId zone = ZoneOffset.UTC;
        Instant start = fallbackStart;
        String rule = null;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.regionMatches(true, 0, "DTSTART", 0, 7)) {
                String body = trimmed.substring(7);
                int colon = body.indexOf(':');
                if (colon < 0) {
                    throw new IllegalArgumentException("invalid DTSTART line: " + trimmed);
                }
                for (String param : body.substring(0, colon).split(";")) {
                    int eq = param.indexOf('=');
                    if (eq > 0 && param.substring(0, eq).trim().equalsIgnoreCase("TZID")) {
                        zone = ZoneId.of(param.substring(eq + 1).trim());
                    }
                }
                start = parseInstant(body.substring(colon + 1).trim(), zone);
            } else if (trimmed.regionMatches(true, 0, "RRULE", 0, 5)) {
                rule = trimmed.substring(5).trim();
                if (rule.startsWith(":")) {
                    rule = rule.substring(1);
                }
            }
        }
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("rrule body is required: RRULE:FREQ=...");
        }

        String freq = null;
        int interval = 1;
        Set<DayOfWeek> byDay = EnumSet.noneOf(DayOfWeek.class);
        Integer byHour = null;
        Integer byMinute = null;
        for (String part : rule.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("invalid RRULE part: " + part);
            }
            String key = part.substring(0, eq).trim().toUpperCase();
            String value = part.substring(eq + 1).trim();
            switch (key) {
                case "FREQ" -> freq = value.toUpperCase();
                case "INTERVAL" -> interval = positiveInt(value, "INTERVAL");
                case "BYDAY" -> byDay = parseByDay(value);
                case "BYHOUR" -> byHour = hour(value);
                case "BYMINUTE" -> byMinute = minute(value);
                case "COUNT", "UNTIL", "BYSECOND", "BYMONTH", "BYMONTHDAY", "BYSETPOS", "WKST" ->
                        throw new IllegalArgumentException("unsupported RRULE part: " + key);
                default -> throw new IllegalArgumentException("unknown RRULE part: " + key);
            }
        }
        if (freq == null || !SUPPORTED_FREQ.contains(freq)) {
            throw new IllegalArgumentException("unsupported RRULE FREQ: " + freq + ", supported=" + SUPPORTED_FREQ);
        }
        return new RRule(text, start, zone, freq, interval, byDay, byHour, byMinute);
    }

    /** 原始规则文本。 */
    public String raw() {
        return raw;
    }

    /** DTSTART 对应的时刻。 */
    public Instant start() {
        return start;
    }

    /** 频率部件。 */
    public String freq() {
        return freq;
    }

    /** 间隔周期数。 */
    public int interval() {
        return interval;
    }

    /**
     * 返回第一个不早于给定时刻的发生时间。
     *
     * @param from 参考时刻
     * @return 发生时间
     */
    public Instant atOrAfter(Instant from) {
        return search(from, false);
    }

    /**
     * 返回第一个严格晚于给定时刻的发生时间。
     *
     * @param from 参考时刻
     * @return 发生时间
     */
    public Instant nextAfter(Instant from) {
        return search(from, true);
    }

    private Instant search(Instant from, boolean strict) {
        ZonedDateTime anchor = start.atZone(zone);
        for (int period = 0; period < MAX_PERIODS; period++) {
            for (ZonedDateTime candidate : occurrences(anchor, period)) {
                Instant instant = candidate.toInstant();
                if (strict ? instant.isAfter(from) : !instant.isBefore(from)) {
                    return instant;
                }
            }
        }
        throw new IllegalStateException("rrule has no occurrence after " + from + ": " + raw);
    }

    private List<ZonedDateTime> occurrences(ZonedDateTime anchor, int period) {
        switch (freq) {
            case "MINUTELY":
                return List.of(anchor.plusMinutes((long) period * interval));
            case "HOURLY":
                return List.of(refineTime(anchor.plusHours((long) period * interval)));
            case "MONTHLY":
                return List.of(refineTime(anchor.plusMonths((long) period * interval)));
            case "DAILY": {
                ZonedDateTime candidate = anchor.plusDays((long) period * interval);
                if (!byDay.isEmpty() && !byDay.contains(candidate.getDayOfWeek())) {
                    return List.of();
                }
                return List.of(refineTime(candidate));
            }
            case "WEEKLY": {
                ZonedDateTime periodBase = anchor.plusWeeks((long) period * interval);
                if (byDay.isEmpty()) {
                    return List.of(refineTime(periodBase));
                }
                var weekStart = periodBase.toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                List<ZonedDateTime> candidates = new ArrayList<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                    if (!byDay.contains(day)) {
                        continue;
                    }
                    var date = weekStart.with(TemporalAdjusters.nextOrSame(day));
                    candidates.add(refineTime(date.atTime(anchor.toLocalTime()).atZone(zone)));
                }
                candidates.sort(Comparator.naturalOrder());
                return candidates;
            }
            default:
                throw new IllegalStateException("unsupported FREQ: " + freq);
        }
    }

    private ZonedDateTime refineTime(ZonedDateTime candidate) {
        ZonedDateTime refined = candidate;
        if (byHour != null) {
            refined = refined.withHour(byHour);
        }
        if (byMinute != null) {
            refined = refined.withMinute(byMinute);
        }
        return refined;
    }

    private static Set<DayOfWeek> parseByDay(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : value.split(",")) {
            String code = token.trim().toUpperCase();
            if (code.length() > 2) {
                throw new IllegalArgumentException("unsupported BYDAY token: " + token);
            }
            days.add(switch (code) {
                case "MO" -> DayOfWeek.MONDAY;
                case "TU" -> DayOfWeek.TUESDAY;
                case "WE" -> DayOfWeek.WEDNESDAY;
                case "TH" -> DayOfWeek.THURSDAY;
                case "FR" -> DayOfWeek.FRIDAY;
                case "SA" -> DayOfWeek.SATURDAY;
                case "SU" -> DayOfWeek.SUNDAY;
                default -> throw new IllegalArgumentException("invalid BYDAY token: " + token);
            });
        }
        return days;
    }

    private static int positiveInt(String value, String part) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(part + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(part + " must be an integer: " + value);
        }
    }

    private static int hour(String value) {
        int parsed = positiveInt(value, "BYHOUR");
        if (parsed > 23) {
            throw new IllegalArgumentException("BYHOUR must be 0..23: " + value);
        }
        return parsed;
    }

    private static int minute(String value) {
        int parsed = positiveInt(value, "BYMINUTE");
        if (parsed > 59) {
            throw new IllegalArgumentException("BYMINUTE must be 0..59: " + value);
        }
        return parsed;
    }

    private static Instant parseInstant(String value, ZoneId zone) {
        String normalized = value.replace("-", "").replace(":", "");
        boolean utc = normalized.endsWith("Z");
        if (utc) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        DateTimeFormatter formatter = normalized.length() == 15
                ? DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                : DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm");
        LocalDateTime local = LocalDateTime.parse(normalized, formatter);
        return local.atZone(utc ? ZoneOffset.UTC : zone).toInstant();
    }
}
