package com.example.plugin;

import net.linlan.dsh.domain.model.entity.AbstractTool;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.spi.AbstractHarnessPlugin;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Java Harness 示例插件。
 * <p>
 * 演示标准插件模板：
 * <ul>
 *   <li>继承 {@link AbstractHarnessPlugin}，获得日志和插件 ID 装配</li>
 *   <li>每个工具继承 {@link AbstractTool}，获得参数转换、Schema 构建和结果工厂</li>
 * </ul>
 *
 * <p>调用流程：接收输入 → 执行本类职责 → 返回结果。</p>
 * <p>示例：根据调用方传入参数完成一次独立操作。</p>
 * 
 */
public class ExampleJavaPlugin extends AbstractHarnessPlugin {

    public ExampleJavaPlugin() {
        super("example-java-plugin");
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(new EchoTool(), new CalculatorTool());
    }

    /** 原样返回输入文本。 */
    static class EchoTool extends AbstractTool {
        @Override public String name() { return "echo"; }
        @Override public String description() { return "Echoes back the provided text message."; }

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
            return ok("Echo: " + str(args, "text"));
        }
    }

    /** 执行基础算术运算。 */
    static class CalculatorTool extends AbstractTool {
        @Override public String name() { return "calculate"; }
        @Override public String description() { return "Performs basic arithmetic (add, subtract, multiply, divide)."; }

        @Override
        public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("operation", enumSchema(
                            "The arithmetic operation",
                            List.of("add", "subtract", "multiply", "divide")))
                    .prop("a", numberSchema("First operand"))
                    .prop("b", numberSchema("Second operand"))
                    .required("operation", "a", "b")
                    .build();
        }
        /**
         * 返回 output Schema。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @return 方法执行结果。
         */


        @Override
        public Map<String, Object> outputSchema() {
            return objectSchema()
                    .prop("result", numberSchema(null))
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
            String op = str(args, "operation");
            double a = dbl(args, "a");
            double b = dbl(args, "b");

            double result;
            try {
                result = switch (op) {
                    case "add" -> a + b;
                    case "subtract" -> a - b;
                    case "multiply" -> a * b;
                    case "divide" -> b == 0 ? Double.NaN : a / b;
                    default -> throw new IllegalArgumentException("unknown operation: " + op);
                };
            } catch (Exception e) {
                return fail("calculation error: " + e.getMessage(), "CALC_ERROR");
            }

            String text = Double.isNaN(result) ? "Error: division by zero" : String.valueOf(result);
            return ok(text);
        }
    }
}
