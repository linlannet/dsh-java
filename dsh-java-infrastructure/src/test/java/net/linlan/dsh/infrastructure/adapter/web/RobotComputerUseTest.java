package net.linlan.dsh.infrastructure.adapter.web;

import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;

import static org.junit.jupiter.api.Assertions.*;

class RobotComputerUseTest {

    private Robot robot;
    private Rectangle bounds;

    @BeforeEach
    void setUp() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        robot = new Robot();
        bounds = new Rectangle(0, 0, 1, 1);
    }

    @Test
    void implementsComputerUsePort() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        assertTrue(cu instanceof IComputerUsePort);
    }

    @Test
    void screenshotReturnsNonEmptyPngBytes() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        byte[] png = cu.screenshot();
        assertNotNull(png);
        assertTrue(png.length > 0);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }

    @Test
    void screenshotWithLargerBounds() {
        Rectangle larger = new Rectangle(0, 0, 10, 10);
        RobotComputerUse cu = new RobotComputerUse(robot, larger);
        byte[] png = cu.screenshot();
        assertNotNull(png);
        assertTrue(png.length > 0);
    }

    @Test
    void mouseClickDoesNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.mouseClick(0, 0);
    }

    @Test
    void mouseMoveDoesNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.mouseMove(1, 1);
    }

    @Test
    void keyTypeEmptyStringIsNoop() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType("");
    }

    @Test
    void keyTypeSingleLowercaseLetter() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType("a");
    }

    @Test
    void keyTypeSingleDigit() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType("1");
    }

    @Test
    void keyTypeWhitespace() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType(" ");
    }

    @Test
    void keyTypeUppercaseLetter() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType("A");
    }

    @Test
    void keyTypeMixedLettersDigitsSpaces() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType("abc 123");
    }

    @Test
    void keyTypeIgnoresPunctuation() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyType(".,!?");
    }

    @Test
    void keyPressEnterDoesNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyPress("enter");
    }

    @Test
    void keyPressTabDoesNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyPress("tab");
    }

    @Test
    void keyPressEscapeDoesNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyPress("escape");
    }

    @Test
    void keyPressSpaceDoesNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyPress("space");
    }

    @Test
    void keyPressArrowKeysDoNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyPress("up");
        cu.keyPress("down");
        cu.keyPress("left");
        cu.keyPress("right");
    }

    @Test
    void keyPressModifierKeysDoNotThrow() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        cu.keyPress("shift");
        cu.keyPress("ctrl");
        cu.keyPress("alt");
    }

    @Test
    void keyPressUnknownThrows() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        assertThrows(IllegalArgumentException.class, () -> cu.keyPress("nonexistent-key-xyz"));
    }

    @Test
    void keyPressNullThrows() {
        RobotComputerUse cu = new RobotComputerUse(robot, bounds);
        assertThrows(IllegalArgumentException.class, () -> cu.keyPress(null));
    }
}
