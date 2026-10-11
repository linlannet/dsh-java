package ${package};

import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 示例工具：回显输入文本。
 * <p>
 * 继承 {@link AbstractTool}，实现 name/description/parameters/run 即可。
 * 参数读取用 {@code str/dbl/intVal/bool}，结果用 {@code ok/fail}。
 *
 * <p>调用流程：接收输入 → 执行本类职责 → 返回结果。</p>
 * <p>示例：根据调用方传入参数完成一次独立操作。</p>
 * 
 */
public class ExampleTool extends AbstractTool {

    @Override
    public String name() {
        return "example_echo";
    }

    @Override
    public String description() {
        return "Echoes back the provided text.";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return objectSchema()
                .prop("text", stringSchema("The text to echo back"))
                .required("text")
                .build();
    }
    /**
     * 运行目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param Map<String Map String参数。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */


    @Override
    protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
        String text = str(args, "text");
        if (text.isBlank()) {
            return fail("text 不能为空", "MISSING_TEXT");
        }
        return ok("Echo: " + text);
    }
}
