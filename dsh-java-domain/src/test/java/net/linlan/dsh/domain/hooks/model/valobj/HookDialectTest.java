package net.linlan.dsh.domain.hooks.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HookDialect 钩子方言枚举")
class HookDialectTest {

    @Test
    @DisplayName("枚举包含 CLAUDE_CODE 与 CODEX 两个值")
    void enumContainsClaudeCodeAndCodex() {
        var values = HookDialect.values();
        assertEquals(2, values.length, "HookDialect 应仅有两个枚举常量");
        assertSame(HookDialect.CLAUDE_CODE, values[0]);
        assertSame(HookDialect.CODEX, values[1]);
    }

    @Test
    @DisplayName("valueOf 可按名称解析 CLAUDE_CODE")
    void valueOfClaudeCode() {
        var dialect = HookDialect.valueOf("CLAUDE_CODE");
        assertSame(HookDialect.CLAUDE_CODE, dialect);
        assertEquals("CLAUDE_CODE", dialect.name());
    }

    @Test
    @DisplayName("valueOf 可按名称解析 CODEX 分支")
    void valueOfCodex() {
        var dialect = HookDialect.valueOf("CODEX");
        assertSame(HookDialect.CODEX, dialect);
        assertEquals("CODEX", dialect.name());
    }

    @Test
    @DisplayName("CODEX 与 CLAUDE_CODE 是不同实例")
    void codexDiffersFromClaudeCode() {
        assertNotSame(HookDialect.CODEX, HookDialect.CLAUDE_CODE);
        assertNotEquals(HookDialect.CODEX, HookDialect.CLAUDE_CODE);
    }

    @Test
    @DisplayName("未知名称抛 IllegalArgumentException")
    void unknownNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> HookDialect.valueOf("UNKNOWN"));
    }

    @Test
    @DisplayName("枚举顺序稳定：CLAUDE_CODE ordinal=0，CODEX ordinal=1")
    void stableOrdinals() {
        assertEquals(0, HookDialect.CLAUDE_CODE.ordinal());
        assertEquals(1, HookDialect.CODEX.ordinal());
    }
}
