package net.linlan.dsh.domain.session.checkpoint.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckpointRollback 回滚结果")
class CheckpointRollbackTest {

    @Test
    @DisplayName("success 创建成功回滚结果")
    void successRollback() {
        var snapshot = CheckpointSnapshot.of("s1", 5, "before-model", "{}");
        var rollback = CheckpointRollback.success(snapshot);

        assertTrue(rollback.success());
        assertTrue(rollback.target().isPresent());
        assertEquals(snapshot, rollback.target().get());
        assertEquals("回滚成功", rollback.message());
    }

    @Test
    @DisplayName("notFound 创建失败回滚结果")
    void notFoundRollback() {
        var rollback = CheckpointRollback.notFound("stepIndex=999");

        assertFalse(rollback.success());
        assertTrue(rollback.target().isEmpty());
        assertTrue(rollback.message().contains("stepIndex=999"));
    }

    @Test
    @DisplayName("null message 抛异常")
    void nullMessageThrows() {
        var snapshot = CheckpointSnapshot.of("s1", 1, "r", "{}");
        assertThrows(NullPointerException.class,
                () -> new CheckpointRollback(snapshot, true, null));
    }
}
