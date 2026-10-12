package net.linlan.dsh.domain.sdk.spill;

import net.linlan.dsh.domain.sdk.spill.adapter.port.ISpillStorePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.034 SpillPolicy 溢出策略")
class SpillPolicyTest {

    @Test
    @DisplayName("shouldSpill 超过阈值返回 true")
    void shouldSpillOverThreshold() {
        var policy = newPolicy();
        assertTrue(policy.shouldSpill("hello world", 5));
    }

    @Test
    @DisplayName("shouldSpill 未超阈值返回 false")
    void shouldSpillUnderThreshold() {
        var policy = newPolicy();
        assertFalse(policy.shouldSpill("hi", 5));
    }

    @Test
    @DisplayName("shouldSpill 等于阈值返回 false")
    void shouldSpillEqualThreshold() {
        var policy = newPolicy();
        assertFalse(policy.shouldSpill("hello", 5));
    }

    @Test
    @DisplayName("shouldSpill null 内容返回 false")
    void shouldSpillNullReturnsFalse() {
        var policy = newPolicy();
        assertFalse(policy.shouldSpill(null, 5));
    }

    @Test
    @DisplayName("apply 超阈值返回占位符与 SpillRef")
    void applyOverThresholdSpills() {
        var policy = newPolicy();
        var result = policy.apply("long content here", 5);
        assertNotNull(result.spillRef());
        assertTrue(result.content().startsWith("[spilled:"));
        assertTrue(result.content().contains("size=17"));
    }

    @Test
    @DisplayName("apply 未超阈值返回原内容且 spillRef 为 null")
    void applyUnderThresholdKeepsContent() {
        var policy = newPolicy();
        var result = policy.apply("hi", 5);
        assertEquals("hi", result.content());
        assertNull(result.spillRef());
    }

    @Test
    @DisplayName("apply null 内容返回 null 内容与 null ref")
    void applyNullContent() {
        var policy = newPolicy();
        var result = policy.apply(null, 5);
        assertNull(result.content());
        assertNull(result.spillRef());
    }

    @Test
    @DisplayName("null store 抛异常")
    void nullStoreThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SpillPolicy(null));
    }

    private SpillPolicy newPolicy() {
        return new SpillPolicy(new SpillStore(new InMemorySpillPort()));
    }

    static final class InMemorySpillPort implements ISpillStorePort {
        final Map<String, String> map = new HashMap<>();

        @Override
        public void save(String refId, String content) {
            map.put(refId, content);
        }

        @Override
        public String load(String refId) {
            return map.get(refId);
        }

        @Override
        public boolean exists(String refId) {
            return map.containsKey(refId);
        }
    }
}
