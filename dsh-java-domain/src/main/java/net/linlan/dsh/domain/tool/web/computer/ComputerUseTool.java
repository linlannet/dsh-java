package net.linlan.dsh.domain.tool.web.computer;

import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;

import java.util.Objects;

public final class ComputerUseTool {

    private final IComputerUsePort port;

    public ComputerUseTool(IComputerUsePort port) {
        Objects.requireNonNull(port, "port");
        this.port = port;
    }

    public byte[] screenshot() {
        byte[] image = port.screenshot();
        if (image == null) {
            throw new IllegalStateException("screenshot returned null");
        }
        return image;
    }

    public void mouseClick(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("coordinates must be non-negative");
        }
        port.mouseClick(x, y);
    }

    public void mouseMove(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("coordinates must be non-negative");
        }
        port.mouseMove(x, y);
    }

    public void keyType(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text required");
        }
        port.keyType(text);
    }

    public void keyPress(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key required");
        }
        port.keyPress(key);
    }
}
