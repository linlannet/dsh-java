package net.linlan.dsh.infrastructure.adapter.web;

import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "harness.extensions.computer-use.enabled", havingValue = "true")
public class RobotComputerUse implements IComputerUsePort {

    private final Robot robot;
    private final Rectangle screenBounds;

    public RobotComputerUse() {
        this(createDefaultRobot(), getDefaultScreenBounds());
    }

    public RobotComputerUse(Robot robot, Rectangle screenBounds) {
        this.robot = robot;
        this.screenBounds = screenBounds;
    }

    private static Robot createDefaultRobot() {
        try {
            return new Robot();
        } catch (AWTException e) {
            throw new IllegalStateException("Failed to create Robot", e);
        }
    }

    private static Rectangle getDefaultScreenBounds() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        return gd.getDefaultConfiguration().getBounds();
    }

    @Override
    public byte[] screenshot() {
        BufferedImage image = robot.createScreenCapture(screenBounds);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to capture screenshot", e);
        }
    }

    @Override
    public void mouseClick(int x, int y) {
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Override
    public void mouseMove(int x, int y) {
        robot.mouseMove(x, y);
    }

    @Override
    public void keyType(String text) {
        for (char c : text.toCharArray()) {
            typeChar(c);
        }
    }

    @Override
    public void keyPress(String key) {
        Integer keyCode = KEY_MAP.get(key);
        if (keyCode == null) {
            throw new IllegalArgumentException("unknown key: " + key);
        }
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    private void typeChar(char c) {
        if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (keyCode != KeyEvent.VK_UNDEFINED) {
                boolean upper = Character.isUpperCase(c);
                if (upper) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                }
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                if (upper) {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
            }
        }
    }

    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("enter", KeyEvent.VK_ENTER);
        KEY_MAP.put("tab", KeyEvent.VK_TAB);
        KEY_MAP.put("escape", KeyEvent.VK_ESCAPE);
        KEY_MAP.put("space", KeyEvent.VK_SPACE);
        KEY_MAP.put("backspace", KeyEvent.VK_BACK_SPACE);
        KEY_MAP.put("delete", KeyEvent.VK_DELETE);
        KEY_MAP.put("up", KeyEvent.VK_UP);
        KEY_MAP.put("down", KeyEvent.VK_DOWN);
        KEY_MAP.put("left", KeyEvent.VK_LEFT);
        KEY_MAP.put("right", KeyEvent.VK_RIGHT);
        KEY_MAP.put("ctrl", KeyEvent.VK_CONTROL);
        KEY_MAP.put("alt", KeyEvent.VK_ALT);
        KEY_MAP.put("shift", KeyEvent.VK_SHIFT);
    }
}
