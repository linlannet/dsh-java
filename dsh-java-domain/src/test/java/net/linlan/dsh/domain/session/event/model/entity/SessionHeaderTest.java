package net.linlan.dsh.domain.session.event.model.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import net.linlan.dsh.domain.session.event.model.valobj.SessionFormatVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.025 SessionHeader 会话头实体校验")
class SessionHeaderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("create 构造当前版本会话头")
    void createBuildsCurrentVersionHeader() {
        SessionHeader header = SessionHeader.create("s-1", NOW, "/cwd", "default");
        assertEquals(SessionHeader.SESSION_FORMAT_VERSION, header.version());
        assertEquals("s-1", header.sessionId());
        assertEquals(NOW, header.createdAt());
        assertEquals("/cwd", header.cwd());
        assertEquals("default", header.agentPreset());
        assertEquals(SessionFormatVersion.CURRENT, header.formatVersion());
        assertEquals(0, header.seedLength());
        assertEquals(0, header.delegationDepth());
    }

    @Test
    @DisplayName("forSubagent 构造子代理会话头")
    void forSubagentBuildsSubagentHeader() {
        SessionHeader header = SessionHeader.forSubagent("s-2", NOW, "/cwd", "s-1", 3, 2, "sub");
        assertEquals("s-2", header.sessionId());
        assertEquals("s-1", header.parentSession());
        assertEquals("subagent", header.origin());
        assertEquals(3, header.seedLength());
        assertEquals(2, header.delegationDepth());
        assertEquals("sub", header.agentPreset());
    }

    @Test
    @DisplayName("向后兼容构造函数 formatVersion 默认 0")
    void legacyConstructorDefaultsFormatVersionZero() {
        SessionHeader header = new SessionHeader(
                SessionHeader.SESSION_FORMAT_VERSION, "s-1", NOW, "/cwd", null, 0, null, 0, "default");
        assertEquals(0, header.formatVersion());
    }

    @Test
    @DisplayName("version 超出上限抛 IllegalArgumentException")
    void versionAboveMaxThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionHeader(SessionHeader.SESSION_FORMAT_VERSION + 1, "s-1", NOW, null, null, 0, null, 0, "p", 0));
    }

    @Test
    @DisplayName("version 为负抛 IllegalArgumentException")
    void negativeVersionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionHeader(-1, "s-1", NOW, null, null, 0, null, 0, "p", 0));
    }

    @Test
    @DisplayName("seedLength 为负抛 IllegalArgumentException")
    void negativeSeedLengthThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionHeader(0, "s-1", NOW, null, null, -1, null, 0, "p", 0));
    }

    @Test
    @DisplayName("delegationDepth 为负抛 IllegalArgumentException")
    void negativeDelegationDepthThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionHeader(0, "s-1", NOW, null, null, 0, null, -1, "p", 0));
    }

    @Test
    @DisplayName("formatVersion 为负抛 IllegalArgumentException")
    void negativeFormatVersionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionHeader(0, "s-1", NOW, null, null, 0, null, 0, "p", -1));
    }

    @Test
    @DisplayName("null sessionId 抛 NullPointerException")
    void nullSessionIdThrows() {
        assertThrows(NullPointerException.class, () ->
                new SessionHeader(0, null, NOW, null, null, 0, null, 0, "p", 0));
    }

    @Test
    @DisplayName("null createdAt 抛 NullPointerException")
    void nullCreatedAtThrows() {
        assertThrows(NullPointerException.class, () ->
                new SessionHeader(0, "s-1", null, null, null, 0, null, 0, "p", 0));
    }

    @Test
    @DisplayName("record 访问器返回构造字段")
    void recordAccessorsReturnFields() {
        SessionHeader header = new SessionHeader(2, "s-1", NOW, "/cwd", "p-1", 1, "subagent", 1, "preset", 3);
        assertNotNull(header);
        assertEquals(2, header.version());
        assertEquals("p-1", header.parentSession());
        assertEquals("subagent", header.origin());
        assertEquals(1, header.delegationDepth());
        assertEquals(3, header.formatVersion());
    }
}
