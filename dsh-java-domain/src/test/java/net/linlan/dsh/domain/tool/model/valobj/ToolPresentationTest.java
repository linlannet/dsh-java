package net.linlan.dsh.domain.tool.model.valobj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.034 ToolPresentation")
class ToolPresentationTest {

    @Test
    @DisplayName("fromName 解析 PTC")
    void fromNamePtc() {
        assertEquals(ToolPresentation.PTC, ToolPresentation.fromName("PTC"));
    }

    @Test
    @DisplayName("fromName 解析 NATIVE")
    void fromNameNative() {
        assertEquals(ToolPresentation.NATIVE, ToolPresentation.fromName("NATIVE"));
    }

    @Test
    @DisplayName("fromName 解析 SELECTOR")
    void fromNameSelector() {
        assertEquals(ToolPresentation.SELECTOR, ToolPresentation.fromName("SELECTOR"));
    }

    @Test
    @DisplayName("fromName 大小写不敏感")
    void fromNameCaseInsensitive() {
        assertEquals(ToolPresentation.PTC, ToolPresentation.fromName("ptc"));
        assertEquals(ToolPresentation.NATIVE, ToolPresentation.fromName("native"));
        assertEquals(ToolPresentation.SELECTOR, ToolPresentation.fromName("selector"));
    }

    @Test
    @DisplayName("fromName 去除前后空白")
    void fromNameTrimsWhitespace() {
        assertEquals(ToolPresentation.PTC, ToolPresentation.fromName("  PTC  "));
    }

    @Test
    @DisplayName("fromName 未知名称抛 IllegalArgumentException")
    void fromNameUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> ToolPresentation.fromName("UNKNOWN"));
    }

    @Test
    @DisplayName("fromName null 抛 IllegalArgumentException")
    void fromNameNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> ToolPresentation.fromName(null));
    }

    @Test
    @DisplayName("fromName 空字符串抛 IllegalArgumentException")
    void fromNameBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> ToolPresentation.fromName(""));
    }

    @Test
    @DisplayName("枚举包含三个值")
    void enumHasThreeValues() {
        assertEquals(3, ToolPresentation.values().length);
    }
}
