package net.linlan.dsh.domain.session.telemetry.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtlpBuffer 字节有界缓冲区")
class OtlpBufferTest {

    @Test
    @DisplayName("默认构造 byteLimit 为 4MB")
    void defaultByteLimitIs4MB() {
        var buffer = new OtlpBuffer();
        assertEquals(4L * 1024 * 1024, buffer.byteLimit());
    }

    @Test
    @DisplayName("offer 事件后 size 和 currentBytes 正确")
    void offerIncrementsSizeAndBytes() {
        var buffer = new OtlpBuffer(1024);
        byte[] event = "hello".getBytes();
        assertTrue(buffer.offer(event));
        assertEquals(1, buffer.size());
        assertEquals(5, buffer.currentBytes());
        assertEquals(0, buffer.droppedCount());
    }

    @Test
    @DisplayName("超界时丢弃最旧事件，新事件入队")
    void dropOldestWhenExceedLimit() {
        var buffer = new OtlpBuffer(10);
        buffer.offer("AAAAA".getBytes()); // 5 bytes
        buffer.offer("BBBBB".getBytes()); // 5 bytes, total 10
        assertEquals(2, buffer.size());
        assertEquals(0, buffer.droppedCount());

        buffer.offer("CCCCC".getBytes()); // 5 bytes, total 15 > 10, drop oldest
        assertEquals(2, buffer.size());
        assertEquals(1, buffer.droppedCount());
        assertEquals(10, buffer.currentBytes());
    }

    @Test
    @DisplayName("单事件超过 byteLimit 时直接丢弃")
    void singleEventExceedsLimitDropped() {
        var buffer = new OtlpBuffer(4);
        byte[] big = "TOOBIG".getBytes(); // 6 bytes > 4
        assertFalse(buffer.offer(big));
        assertEquals(0, buffer.size());
        assertEquals(1, buffer.droppedCount());
    }

    @Test
    @DisplayName("byteLimit 非正抛异常")
    void nonPositiveLimitRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OtlpBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new OtlpBuffer(-1));
    }

    @Test
    @DisplayName("连续超界丢弃多个最旧事件")
    void dropMultipleOldest() {
        var buffer = new OtlpBuffer(10);
        buffer.offer("AA".getBytes()); // 2
        buffer.offer("BB".getBytes()); // 2
        buffer.offer("CC".getBytes()); // 2
        buffer.offer("DD".getBytes()); // 2
        buffer.offer("EE".getBytes()); // 2, total 10
        assertEquals(5, buffer.size());

        buffer.offer("FFFFF".getBytes()); // 5 bytes, need to drop oldest until fit
        assertTrue(buffer.droppedCount() >= 1);
        assertTrue(buffer.currentBytes() <= 10);
    }
}
