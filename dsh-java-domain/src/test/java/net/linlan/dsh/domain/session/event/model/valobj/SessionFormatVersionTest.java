package net.linlan.dsh.domain.session.event.model.valobj;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionFormatVersion 值对象")
class SessionFormatVersionTest {

    @Test
    @DisplayName("CURRENT 常量为 4")
    void currentIsFour() {
        assertEquals(4, SessionFormatVersion.CURRENT);
    }

    @Test
    @DisplayName("needsMigration() 对 version < 4 返回 true")
    void needsMigrationForOldVersions() {
        assertTrue(SessionFormatVersion.of(0).needsMigration());
        assertTrue(SessionFormatVersion.of(1).needsMigration());
        assertTrue(SessionFormatVersion.of(2).needsMigration());
        assertTrue(SessionFormatVersion.of(3).needsMigration());
    }

    @Test
    @DisplayName("needsMigration() 对 version >= 4 返回 false")
    void noMigrationNeededForCurrentOrNewer() {
        assertFalse(SessionFormatVersion.of(4).needsMigration());
        assertFalse(SessionFormatVersion.of(5).needsMigration());
        assertFalse(SessionFormatVersion.of(100).needsMigration());
    }

    @Test
    @DisplayName("of() 和 value() 正确往返")
    void ofAndValueRoundTrip() {
        assertEquals(3, SessionFormatVersion.of(3).value());
        assertEquals(4, SessionFormatVersion.of(4).value());
        assertEquals(0, SessionFormatVersion.of(0).value());
    }

    @Test
    @DisplayName("isCurrent() 对 version == 4 返回 true")
    void isCurrent() {
        assertFalse(SessionFormatVersion.of(3).isCurrent());
        assertTrue(SessionFormatVersion.of(4).isCurrent());
        assertFalse(SessionFormatVersion.of(5).isCurrent());
    }

    @Test
    @DisplayName("负版本号抛出 IllegalArgumentException")
    void negativeVersionRejected() {
        assertThrows(IllegalArgumentException.class, () -> SessionFormatVersion.of(-1));
    }

    @Test
    @DisplayName("SessionHeader.create 设置 formatVersion 为 CURRENT")
    void headerCreateSetsFormatVersion() {
        var header = SessionHeader.create("s1", Instant.EPOCH, "/tmp", null);
        assertEquals(SessionFormatVersion.CURRENT, header.formatVersion());
    }

    @Test
    @DisplayName("SessionHeader 向后兼容构造函数 formatVersion 默认 0")
    void headerLegacyConstructorDefaultsFormatVersionToZero() {
        var header = new SessionHeader(
                3, "s1", Instant.EPOCH, null, null, 0, null, 0, null);
        assertEquals(0, header.formatVersion());
        assertTrue(SessionFormatVersion.of(header.formatVersion()).needsMigration());
    }
}
