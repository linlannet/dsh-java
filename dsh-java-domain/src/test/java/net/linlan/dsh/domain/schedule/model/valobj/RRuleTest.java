package net.linlan.dsh.domain.schedule.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RRule 解析与展开")
class RRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-13T01:05:00Z");

    @Test
    @DisplayName("DAILY 在 TZID 下计算正确次日")
    void dailyAdvancesOneDay() {
        RRule rule = RRule.parse(
                "DTSTART;TZID=Asia/Shanghai:20261013T090000\nRRULE:FREQ=DAILY", NOW);
        Instant first = rule.atOrAfter(Instant.parse("2026-10-12T00:00:00Z"));
        assertEquals(Instant.parse("2026-10-13T01:00:00Z"), first);
        Instant next = rule.nextAfter(first);
        assertEquals(Instant.parse("2026-10-14T01:00:00Z"), next);
    }

    @Test
    @DisplayName("DAILY 无 DTSTART 时用 fallback 起点")
    void dailyUsesFallbackStart() {
        RRule rule = RRule.parse("RRULE:FREQ=DAILY", Instant.parse("2026-10-13T01:00:00Z"));
        assertEquals(Instant.parse("2026-10-13T01:00:00Z"), rule.atOrAfter(Instant.MIN));
        assertEquals(Instant.parse("2026-10-14T01:00:00Z"), rule.nextAfter(Instant.parse("2026-10-13T01:00:00Z")));
    }

    @Test
    @DisplayName("DAILY 带 BYHOUR/BYMINUTE 且 DTSTART 已过去")
    void dailyWithByPartsPast() {
        RRule rule = RRule.parse(
                "DTSTART;TZID=UTC:20261013T000000\nRRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=30", NOW);
        Instant first = rule.atOrAfter(NOW);
        assertEquals(Instant.parse("2026-10-13T09:30:00Z"), first);
    }

    @Test
    @DisplayName("WEEKLY 按 BYDAY 展开")
    void weeklyByDay() {
        RRule rule = RRule.parse(
                "DTSTART;TZID=UTC:20261012T000000\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE", NOW);
        // 2026-10-12 是周一；NOW=10-13(周二) 之后下一次是 10-14(周三)
        Instant next = rule.nextAfter(NOW);
        assertEquals(Instant.parse("2026-10-14T00:00:00Z"), next);
    }

    @Test
    @DisplayName("HOURLY 与 MINUTELY 步进")
    void hourlyAndMinutely() {
        RRule hourly = RRule.parse("RRULE:FREQ=HOURLY", Instant.parse("2026-10-13T01:00:00Z"));
        assertEquals(Instant.parse("2026-10-13T02:00:00Z"), hourly.nextAfter(Instant.parse("2026-10-13T01:00:00Z")));
        RRule minutely = RRule.parse("RRULE:FREQ=MINUTELY", Instant.parse("2026-10-13T01:00:00Z"));
        assertEquals(Instant.parse("2026-10-13T01:01:00Z"), minutely.nextAfter(Instant.parse("2026-10-13T01:00:00Z")));
    }

    @Test
    @DisplayName("未实现部件显式拒绝")
    void unsupportedPartsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RRule.parse("RRULE:FREQ=DAILY;COUNT=5", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> RRule.parse("RRULE:FREQ=MONTHLY;BYMONTHDAY=1", NOW));
    }

    @Test
    @DisplayName("缺少 RRULE 主体或 FREQ 非法抛错")
    void missingOrBadFreq() {
        assertThrows(IllegalArgumentException.class, () -> RRule.parse("DTSTART:20261013T000000", NOW));
        assertThrows(IllegalArgumentException.class, () -> RRule.parse("RRULE:FREQ=YEARLY", NOW));
        assertThrows(IllegalArgumentException.class, () -> RRule.parse("", NOW));
    }

    @Test
    @DisplayName("UTC 的 Z 后缀解析")
    void utcZSuffix() {
        RRule rule = RRule.parse("DTSTART:20261013T010000Z\nRRULE:FREQ=DAILY", Instant.EPOCH);
        assertEquals(Instant.parse("2026-10-13T01:00:00Z"), rule.start());
    }
}
