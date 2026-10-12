package net.linlan.dsh.domain.session.checkpoint.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckpointSnapshot 检查点快照 record")
class CheckpointSnapshotTest {

    @Test
    @DisplayName("字段按构造顺序正确赋值")
    void fieldsAssignedInOrder() {
        var now = Instant.now();
        var snap = new CheckpointSnapshot("s-1", 7L, "before-model", "{\"k\":1}", now);
        assertEquals("s-1", snap.sessionId());
        assertEquals(7L, snap.stepIndex());
        assertEquals("before-model", snap.reason());
        assertEquals("{\"k\":1}", snap.contextSnapshotJson());
        assertEquals(now, snap.createdAt());
    }

    @Test
    @DisplayName("sessionId 为 null 抛 NullPointerException")
    void nullSessionIdRejected() {
        assertThrows(NullPointerException.class,
                () -> new CheckpointSnapshot(null, 0, "r", "{}", Instant.now()));
    }

    @Test
    @DisplayName("reason 为 null 抛 NullPointerException")
    void nullReasonRejected() {
        assertThrows(NullPointerException.class,
                () -> new CheckpointSnapshot("s", 0, null, "{}", Instant.now()));
    }

    @Test
    @DisplayName("createdAt 为 null 抛 NullPointerException")
    void nullCreatedAtRejected() {
        assertThrows(NullPointerException.class,
                () -> new CheckpointSnapshot("s", 0, "r", "{}", null));
    }

    @Test
    @DisplayName("contextSnapshotJson 允许 null")
    void nullContextJsonAllowed() {
        var snap = new CheckpointSnapshot("s", 0, "r", null, Instant.now());
        assertNull(snap.contextSnapshotJson());
    }

    @Test
    @DisplayName("of 工厂方法用 Instant.now 填充 createdAt")
    void ofFactoryFillsCreatedAt() {
        var before = Instant.now();
        var snap = CheckpointSnapshot.of("s-2", 3, "before-tool", "{}");
        var after = Instant.now();
        assertEquals("s-2", snap.sessionId());
        assertEquals(3L, snap.stepIndex());
        assertEquals("before-tool", snap.reason());
        assertNotNull(snap.createdAt());
        assertTrue(!snap.createdAt().isBefore(before) && !snap.createdAt().isAfter(after),
                "of 生成的 createdAt 应在调用前后 Instant 之间");
    }

    @Test
    @DisplayName("record equals/hashCode 按全部字段判定")
    void recordEqualsHashCode() {
        var now = Instant.now();
        var a = new CheckpointSnapshot("s", 1, "r", "{}", now);
        var b = new CheckpointSnapshot("s", 1, "r", "{}", now);
        var c = new CheckpointSnapshot("s", 2, "r", "{}", now);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
