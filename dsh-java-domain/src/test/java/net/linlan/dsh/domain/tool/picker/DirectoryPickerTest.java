package net.linlan.dsh.domain.tool.picker;

import net.linlan.dsh.domain.tool.picker.adapter.port.IDirectoryPickerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectoryPicker 目录选择器")
class DirectoryPickerTest {

    @Test
    @DisplayName("pick 委派 port 返回路径")
    void pickDelegates() {
        var port = new StubPort("/selected/path");
        var picker = new DirectoryPicker(port);
        assertEquals("/selected/path", picker.pick("/start"));
    }

    @Test
    @DisplayName("pick 空白 startPath 抛出 IllegalArgumentException")
    void pickBlankThrows() {
        var port = new StubPort("/x");
        var picker = new DirectoryPicker(port);
        assertThrows(IllegalArgumentException.class, () -> picker.pick(""));
        assertThrows(IllegalArgumentException.class, () -> picker.pick("  "));
    }

    @Test
    @DisplayName("pick null startPath 抛出 NPE")
    void pickNullThrows() {
        var port = new StubPort("/x");
        var picker = new DirectoryPicker(port);
        assertThrows(NullPointerException.class, () -> picker.pick(null));
    }

    @Test
    @DisplayName("port 返回 null 抛出 IllegalStateException")
    void portReturnsNullThrows() {
        var port = new StubPort(null);
        var picker = new DirectoryPicker(port);
        assertThrows(IllegalStateException.class, () -> picker.pick("/start"));
    }

    @Test
    @DisplayName("port 返回空白抛出 IllegalStateException")
    void portReturnsBlankThrows() {
        var port = new StubPort("  ");
        var picker = new DirectoryPicker(port);
        assertThrows(IllegalStateException.class, () -> picker.pick("/start"));
    }

    @Test
    @DisplayName("构造 null port 抛出 NPE")
    void nullPortThrows() {
        assertThrows(NullPointerException.class, () -> new DirectoryPicker(null));
    }

    private static final class StubPort implements IDirectoryPickerPort {
        private final String result;

        StubPort(String result) {
            this.result = result;
        }

        @Override
        public String pick(String startPath) {
            return result;
        }
    }
}
