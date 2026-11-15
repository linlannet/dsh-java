package net.linlan.dsh.domain.tool.web.computer;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import net.linlan.dsh.domain.tool.web.computer.adapter.port.IComputerUsePort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CuaRunCodeTool 为未组合 preset 的 Agent 提供 CUA 程序化入口。
 *
 * <p>调用流程：按 {@code steps} 顺序执行 CUA 动作 → 按执行顺序收集文本/图片。</p>
 * <p>示例：先 screenshot 再 mouse_click，结果中图片先于点击文本出现。</p>
 */
public class CuaRunCodeTool implements ToolDefinition {

    public static final String NAME = "cua_run_code";
    private static final String DESCRIPTION =
            "Run a short CUA program against the local desktop. Provide ordered steps; each step has "
                    + "action (screenshot/mouse_click/mouse_move/key_type/key_press) plus x/y/text/key. "
                    + "Outputs are returned in step order, screenshots as images.";

    private final CuaTool cua;

    public CuaRunCodeTool(IComputerUsePort port) {
        this.cua = new CuaTool(port);
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
                        "steps", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "action", Map.of(
                                                        "type", "string",
                                                        "enum", List.of("screenshot", "mouse_click",
                                                                "mouse_move", "key_type", "key_press")
                                                ),
                                                "x", Map.of("type", "integer"),
                                                "y", Map.of("type", "integer"),
                                                "text", Map.of("type", "string"),
                                                "key", Map.of("type", "string")
                                        ),
                                        "required", List.of("action")
                                )
                        )
                ),
                "required", List.of("steps")
        );
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext exec) {
        if (!(args instanceof Map<?, ?> map) || !(map.get("steps") instanceof List<?> steps)) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail(new ToolArgsError("steps is required").toFailure()));
        }
        List<ContentBlock> content = new ArrayList<>();
        for (Object step : steps) {
            if (!(step instanceof Map<?, ?>)) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail(new ToolArgsError("each step must be an object").toFailure()));
            }
            ToolExecutionResult result = cua.execute(step, exec).join();
            if (result.isError()) {
                return CompletableFuture.completedFuture(result);
            }
            content.addAll(result.content());
        }
        return CompletableFuture.completedFuture(
                new ToolExecutionResult.Success(null, content, null, List.of(), false));
    }
}
