package net.linlan.dsh.domain.terminal.model.valobj;

import net.linlan.dsh.domain.common.error.HarnessError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TerminalBackendCleanupError 清理失败语义")
class TerminalBackendCleanupErrorTest {

    @Test
    @DisplayName("继承 TerminalError 并保持 CLEANUP_FAILED 码")
    void cleanupErrorIdentity() {
        var cleanup = new IOException("destroy failed");
        var error = new TerminalBackendCleanupError("cleanup failed", cleanup);

        assertInstanceOf(TerminalError.class, error);
        assertInstanceOf(HarnessError.class, error);
        assertEquals(TerminalBackendCleanupError.CLEANUP_FAILED, error.errorCode());
        assertEquals("TerminalBackendCleanupError", error.errorName());
        assertSame(cleanup, error.cleanupFailure());
        assertSame(cleanup, error.getCause());
    }

    @Test
    @DisplayName("同时聚合启动失败与清理失败")
    void aggregatesStartAndCleanupFailures() {
        var start = new IOException("start failed");
        var cleanup = new IOException("cleanup failed");
        var error = new TerminalBackendCleanupError("aggregate", start, cleanup);

        assertTrue(error.isAggregate());
        assertSame(start, error.startFailure());
        assertSame(cleanup, error.cleanupFailure());
        assertSame(cleanup, error.getCause());
        assertEquals(1, error.getSuppressed().length);
        assertSame(start, error.getSuppressed()[0]);
    }

    @Test
    @DisplayName("仅清理失败时不标记为聚合")
    void cleanupOnlyIsNotAggregate() {
        var cleanup = new IOException("cleanup failed");
        var error = new TerminalBackendCleanupError("cleanup only", cleanup);

        assertFalse(error.isAggregate());
        assertEquals(0, error.getSuppressed().length);
    }

    @Test
    @DisplayName("TerminalError 提供会话查找与运行状态错误码")
    void terminalErrorCodes() {
        assertEquals("TERMINAL_SESSION_NOT_FOUND", TerminalError.SESSION_NOT_FOUND);
        assertEquals("TERMINAL_SESSION_NOT_RUNNING", TerminalError.SESSION_NOT_RUNNING);

        var notFound = new TerminalError(TerminalError.SESSION_NOT_FOUND, "missing");
        assertEquals("TerminalError", notFound.errorName());
    }
}
