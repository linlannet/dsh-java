package net.linlan.dsh.domain.bot;

import net.linlan.dsh.domain.bot.service.FeishuEventDeduplicator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书事件去重器单元测试：重复判定与有界淘汰。
 */
class FeishuEventDeduplicatorTest {

    @Test
    void firstSeenIsNotDuplicateAndRepeatIs() {
        FeishuEventDeduplicator dedup = new FeishuEventDeduplicator();
        assertFalse(dedup.isDuplicate("m1"));
        assertTrue(dedup.isDuplicate("m1"));
    }

    @Test
    void nullAndBlankAreNeverDuplicate() {
        FeishuEventDeduplicator dedup = new FeishuEventDeduplicator();
        assertFalse(dedup.isDuplicate(null));
        assertFalse(dedup.isDuplicate("  "));
    }

    @Test
    void boundedEvictionAllowsReplayAfterWindow() {
        FeishuEventDeduplicator dedup = new FeishuEventDeduplicator();
        assertFalse(dedup.isDuplicate("first"));
        for (int i = 0; i < 2000; i++) {
            dedup.isDuplicate("filler-" + i);
        }
        assertFalse(dedup.isDuplicate("first"));
    }
}