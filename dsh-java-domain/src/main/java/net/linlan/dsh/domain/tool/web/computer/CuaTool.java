package net.linlan.dsh.domain.tool.web.computer;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CuaTool 向 Agent 暴露原始 CUA 动作（screenshot / mouse / keyboard）。
 *
 * <p>调用流程：参数校验 → {@link ComputerUseTool} → 图片或文本结果。</p>
 * <p>示例：{@code {"action":"screenshot"}} 返回 {@link ImageBlock}。</p>
 */
public class CuaTool implements ToolDefinition {

    public static final String NAME = "cua";
    private static final String DESCRIPTION =
            "Control the local desktop directly. Actions: screenshot, mouse_click, mouse_move, "
                    + "key_type, key_press. Screenshots return an image block.";

    private final ComputerUseTool computerUse;

    public CuaTool(IComputerUsePort port) {
        this.computerUse = new ComputerUseTool(port);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("screenshot", "mouse_click", "mouse_move", "key_type", "key_press")
                        ),
                        "x", Map.of("type", "integer"),
                        "y", Map.of("type", "integer"),
                        "text", Map.of("type", "string"),
                        "key", Map.of("type", "string")
                ),
                "required", List.of("action")
        );
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext exec) {
        try {
            Map<String, Object> map = (Map<String, Object>) args;
            String action = String.valueOf(map.get("action"));
            return CompletableFuture.completedFuture(switch (action) {
                case "screenshot" -> screenshotResult();
                case "mouse_click" -> {
                    computerUse.mouseClick(intArg(map, "x"), intArg(map, "y"));
                    yield ToolExecutionResult.ok(List.of(new TextBlock("mouse_click ok")));
                }
                case "mouse_move" -> {
                    computerUse.mouseMove(intArg(map, "x"), intArg(map, "y"));
                    yield ToolExecutionResult.ok(List.of(new TextBlock("mouse_move ok")));
                }
                case "key_type" -> {
                    computerUse.keyType(stringArg(map, "text"));
                    yield ToolExecutionResult.ok(List.of(new TextBlock("key_type ok")));
                }
                case "key_press" -> {
                    computerUse.keyPress(stringArg(map, "key"));
                    yield ToolExecutionResult.ok(List.of(new TextBlock("key_press ok")));
                }
                default -> ToolExecutionResult.fail(
                        new ToolArgsError("unsupported action: " + action).toFailure());
            });
        } catch (net.linlan.dsh.domain.common.error.HarnessError error) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(error.toFailure()));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail(error.getMessage(), "CUA_EXECUTION_FAILED"));
        }
    }

    private ToolExecutionResult screenshotResult() {
        byte[] image = computerUse.screenshot();
        ImageBlock block = new ImageBlock(new ImageAttachmentRef(image, "image/png", 0, 0));
        return ToolExecutionResult.ok(List.of(new TextBlock("[image] screenshot"), block));
    }

    private static int intArg(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new ToolArgsError(key + " must be an integer");
    }

    private static String stringArg(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new ToolArgsError(key + " is required");
    }
}
