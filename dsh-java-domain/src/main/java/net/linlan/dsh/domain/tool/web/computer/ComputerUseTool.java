package net.linlan.dsh.domain.tool.web.computer;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;

import java.util.Objects;

/**
 * ComputerUseTool 封装桌面操作（截图、鼠标、键盘）。
 *
 * <p>调用流程：参数校验 → 调用 {@link IComputerUsePort} → 失败时抛出分类错误。</p>
 * <p>示例：坐标非法返回 {@code INVALID_ARGS}，端口异常返回 {@code CUA_EXECUTION_FAILED}。</p>
 */
public final class ComputerUseTool {

    private final IComputerUsePort port;

    public ComputerUseTool(IComputerUsePort port) {
        Objects.requireNonNull(port, "port");
        this.port = port;
    }

    public byte[] screenshot() {
        byte[] image = call("screenshot", port::screenshot);
        if (image == null) {
            throw new ComputerUseError(ComputerUseError.EMPTY_RESULT,
                    "computer use screenshot returned null", null);
        }
        return image;
    }

    public void mouseClick(int x, int y) {
        requireCoordinates(x, y);
        run("mouseClick", () -> port.mouseClick(x, y));
    }

    public void mouseMove(int x, int y) {
        requireCoordinates(x, y);
        run("mouseMove", () -> port.mouseMove(x, y));
    }

    public void keyType(String text) {
        if (text == null || text.isEmpty()) {
            throw new ToolArgsError("text required");
        }
        run("keyType", () -> port.keyType(text));
    }

    public void keyPress(String key) {
        if (key == null || key.isBlank()) {
            throw new ToolArgsError("key required");
        }
        run("keyPress", () -> port.keyPress(key));
    }

    private void requireCoordinates(int x, int y) {
        if (x < 0 || y < 0) {
            throw new ToolArgsError("coordinates must be non-negative");
        }
    }

    private <T> T call(String action, java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (HarnessError error) {
            throw error;
        } catch (RuntimeException error) {
            throw new ComputerUseError(ComputerUseError.EXECUTION_FAILED,
                    "computer use " + action + " failed: " + error.getMessage(), error);
        }
    }

    private void run(String action, Runnable operation) {
        call(action, () -> {
            operation.run();
            return null;
        });
    }
}
