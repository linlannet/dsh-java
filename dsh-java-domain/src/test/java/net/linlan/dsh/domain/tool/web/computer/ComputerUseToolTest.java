package net.linlan.dsh.domain.tool.web.computer;

import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.037 ComputerUseTool 计算机使用工具")
class ComputerUseToolTest {

    @Test
    @DisplayName("screenshot 返回字节数组")
    void screenshotReturnsBytes() {
        var port = new StubComputerPort(new byte[]{1, 2, 3});
        var tool = new ComputerUseTool(port);
        byte[] result = tool.screenshot();
        assertArrayEquals(new byte[]{1, 2, 3}, result);
    }

    @Test
    @DisplayName("screenshot null 返回抛异常")
    void screenshotNullThrows() {
        var port = new StubComputerPort(null);
        var tool = new ComputerUseTool(port);
        assertThrows(IllegalStateException.class, tool::screenshot);
    }

    @Test
    @DisplayName("mouseClick 调用端口")
    void mouseClickDelegates() {
        var port = new StubComputerPort(new byte[0]);
        var tool = new ComputerUseTool(port);
        tool.mouseClick(100, 200);
        assertEquals(100, port.lastClickX);
        assertEquals(200, port.lastClickY);
    }

    @Test
    @DisplayName("mouseClick 负坐标抛异常")
    void mouseClickNegativeThrows() {
        var tool = new ComputerUseTool(new StubComputerPort(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> tool.mouseClick(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> tool.mouseClick(0, -1));
    }

    @Test
    @DisplayName("mouseMove 调用端口")
    void mouseMoveDelegates() {
        var port = new StubComputerPort(new byte[0]);
        var tool = new ComputerUseTool(port);
        tool.mouseMove(50, 60);
        assertEquals(50, port.lastMoveX);
        assertEquals(60, port.lastMoveY);
    }

    @Test
    @DisplayName("mouseMove 负坐标抛异常")
    void mouseMoveNegativeThrows() {
        var tool = new ComputerUseTool(new StubComputerPort(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> tool.mouseMove(-1, 0));
    }

    @Test
    @DisplayName("keyType 调用端口")
    void keyTypeDelegates() {
        var port = new StubComputerPort(new byte[0]);
        var tool = new ComputerUseTool(port);
        tool.keyType("hello");
        assertEquals("hello", port.lastTypedText);
    }

    @Test
    @DisplayName("keyType 空文本抛异常")
    void keyTypeEmptyThrows() {
        var tool = new ComputerUseTool(new StubComputerPort(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> tool.keyType(""));
        assertThrows(IllegalArgumentException.class, () -> tool.keyType(null));
    }

    @Test
    @DisplayName("keyPress 调用端口")
    void keyPressDelegates() {
        var port = new StubComputerPort(new byte[0]);
        var tool = new ComputerUseTool(port);
        tool.keyPress("enter");
        assertEquals("enter", port.lastPressedKey);
    }

    @Test
    @DisplayName("keyPress 空键抛异常")
    void keyPressEmptyThrows() {
        var tool = new ComputerUseTool(new StubComputerPort(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> tool.keyPress(""));
        assertThrows(IllegalArgumentException.class, () -> tool.keyPress(null));
    }

    @Test
    @DisplayName("null port 抛异常")
    void nullPortThrows() {
        assertThrows(NullPointerException.class, () -> new ComputerUseTool(null));
    }

    static final class StubComputerPort implements IComputerUsePort {
        final byte[] screenshotResult;
        int lastClickX = -1;
        int lastClickY = -1;
        int lastMoveX = -1;
        int lastMoveY = -1;
        String lastTypedText;
        String lastPressedKey;

        StubComputerPort(byte[] screenshotResult) {
            this.screenshotResult = screenshotResult;
        }

        @Override
        public byte[] screenshot() {
            return screenshotResult;
        }

        @Override
        public void mouseClick(int x, int y) {
            lastClickX = x;
            lastClickY = y;
        }

        @Override
        public void mouseMove(int x, int y) {
            lastMoveX = x;
            lastMoveY = y;
        }

        @Override
        public void keyType(String text) {
            lastTypedText = text;
        }

        @Override
        public void keyPress(String key) {
            lastPressedKey = key;
        }
    }
}
