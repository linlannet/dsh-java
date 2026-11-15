package net.linlan.dsh.domain.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("HarnessError 统一错误基类")
class HarnessErrorTest {

    @Test
    @DisplayName("默认使用具体类名作为错误名")
    void defaultNameUsesConcreteClassName() {
        var error = new HarnessError("CUSTOM_CODE", "boom");

        assertEquals("CUSTOM_CODE", error.errorCode());
        assertEquals("HarnessError", error.errorName());
        assertEquals("boom", error.getMessage());
    }

    @Test
    @DisplayName("显式错误名优先于类名")
    void explicitNameWins() {
        var error = new HarnessError("CUSTOM_CODE", "StableName", "boom", null);

        assertEquals("CUSTOM_CODE", error.errorCode());
        assertEquals("StableName", error.errorName());
    }

    @Test
    @DisplayName("空错误码回落为 TOOL_ERROR，空消息回落为错误码")
    void blankCodeAndMessageFallBack() {
        var error = new HarnessError("  ", "  ");

        assertEquals("TOOL_ERROR", error.errorCode());
        assertEquals("TOOL_ERROR", error.getMessage());
    }

    @Test
    @DisplayName("保留底层 cause")
    void keepsCause() {
        var cause = new IllegalStateException("root");
        var error = new HarnessError("CODE", "boom", cause);

        assertSame(cause, error.getCause());
    }

    @Test
    @DisplayName("StorageException 继承 HarnessError")
    void storageExceptionIsHarnessError() {
        assertThrows(HarnessError.class, () -> {
            throw new net.linlan.dsh.domain.storage.model.valobj.StorageException(
                    net.linlan.dsh.domain.storage.model.valobj.StorageErrorCode.UNIT_NOT_FOUND,
                    "missing");
        });
    }
}
