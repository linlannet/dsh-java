package net.linlan.dsh.domain.identity.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnonymousUserId 匿名标识")
class AnonymousUserIdTest {

    @Test
    @DisplayName("generate 生成随机 UUID")
    void generateRandom() {
        var id1 = AnonymousUserId.generate();
        var id2 = AnonymousUserId.generate();
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    @DisplayName("of 从字符串创建")
    void ofString() {
        var id = AnonymousUserId.of("test-uuid");
        assertEquals("test-uuid", id.value());
    }

    @Test
    @DisplayName("null 值抛 NullPointerException")
    void nullValueThrows() {
        assertThrows(NullPointerException.class, () -> AnonymousUserId.of(null));
    }

    @Test
    @DisplayName("空白值抛 IllegalArgumentException")
    void blankValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> AnonymousUserId.of("  "));
    }
}
