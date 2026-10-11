package net.linlan.dsh.domain.model.entity;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ToolDefinition} 的通用基类。
 * <p>
 * 统一封装工具实现中重复出现的样板逻辑：参数 Map 类型转换、JSON Schema 组装，
 * 以及 {@link ToolExecutionResult} / {@link TextBlock} 包装。子类只需实现三个方法，
 * 即可获得类型安全的执行入口：
 *
 * <ul>
 *   <li>{@link #name()} — tool name</li>
 *   <li>{@link #description()} — model-facing description</li>
 *   <li>{@link #parameters()} — JSON Schema for arguments</li>
 * </ul>
 *
 * 执行入口 {@link #execute(Object, ToolRunContext)} 会先把原始参数安全转换为
 * {@code Map<String, Object>}，再委托给 {@link #run(Map, ToolRunContext)}。
 * 子类只需覆写 {@link #run}，无需重复处理类型转换和抑制告警。
 *
 * <pre>{@code
 * public class EchoTool extends AbstractTool {
 *     @Override public String name() { return "echo"; }
 *     @Override public String description() { return "Echoes text back."; }
 *     @Override public Map<String, Object> parameters() {
 *         return objectSchema()
 *             .prop("text", stringSchema("The text to echo"))
 *             .required("text")
 *             .build();
 *     }
 *     @Override
 *     protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
 *         String text = str(args, "text");
 *         return ok("Echo: " + text);
 *     }
 * }
 * }</pre>
 *
 * <h3>子类可用速查</h3>
 * <ul>
 *   <li><b>参数读取</b>：{@code str(args,key)} / {@code str(args,key,default)} /
 *       {@code dbl(args,key)} / {@code intVal(args,key[,default])} / {@code bool(args,key)}</li>
 *   <li><b>结果工厂</b>：{@code ok(text)} / {@code ok(List<ContentBlock>)} /
 *       {@code fail(message,code)}</li>
 *   <li><b>Schema 构建</b>：{@code objectSchema().prop(k,v).required(k).build()} +
 *       {@code stringSchema/numberSchema/integerSchema/booleanSchema/enumSchema}</li>
 *   <li><b>可选 override</b>：{@code isConcurrencySafe(args)} 返回 true 开启并发；
 *       {@code timeoutMs()} 设置协作超时</li>
 * </ul>
 */
public abstract class AbstractTool implements ToolDefinition {

    /**
     * 默认输出 Schema 是宽松的 object。需要更严格的契约时可以覆写。
     */
    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object");
    }

    /**
     * Harness 调用的入口。先将 {@code args} 转为 Map，再委托给
     * {@link #run(Map, ToolRunContext)}。
     * <p>
     * 如果 {@code args} 不是 Map（模型输出异常），方法不会抛异常，而是返回
     * {@code INVALID_ARGS} 失败结果。
     */
    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        Map<String, Object> argMap;
        if (args instanceof Map<?, ?> raw) {
            argMap = (Map<String, Object>) raw;
        } else if (args == null) {
            argMap = Map.of();
        } else {
            return fail("expected object arguments, got: " + args.getClass().getSimpleName(),
                    "INVALID_ARGS");
        }
        try {
            return run(argMap, ctx);
        } catch (Exception e) {
            return fail("unexpected error: " + e.getMessage(), "TOOL_ERROR");
        }
    }

    /**
     * 子类业务逻辑。参数已经完成安全类型转换。
     *
     * @param args 类型化参数 Map，永不为 {@code null}
     * @param ctx  运行上下文，用于延迟消息和回合收尾
     * @return 执行结果 Future
     */
    protected abstract CompletableFuture<ToolExecutionResult> run(
            Map<String, Object> args, ToolRunContext ctx);

    // ── 结果工厂 ───────────────────────────────────────────────────────

    /** 返回单文本块成功结果。 */
    protected CompletableFuture<ToolExecutionResult> ok(String text) {
        return completed(ToolExecutionResult.ok(List.of(new TextBlock(text))));
    }

    /** 返回指定内容块成功结果。 */
    protected CompletableFuture<ToolExecutionResult> ok(List<ContentBlock> content) {
        return completed(ToolExecutionResult.ok(content));
    }

    /** 返回带消息和错误码的失败结果。 */
    protected CompletableFuture<ToolExecutionResult> fail(String message, String code) {
        return completed(ToolExecutionResult.fail(message, code));
    }

    /**
     * 处理completed。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param r r参数。
     * @return 方法执行结果。
     */
    private static CompletableFuture<ToolExecutionResult> completed(ToolExecutionResult r) {
        return CompletableFuture.completedFuture(r);
    }

    // ── argument helpers ───────────────────────────────────────────────

    /** 读取必填字符串参数；缺失时返回空字符串。 */
    protected static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** 读取字符串参数，支持默认值。 */
    protected static String str(Map<String, Object> args, String key, String defaultValue) {
        Object v = args.get(key);
        return v == null ? defaultValue : String.valueOf(v).trim();
    }

    /** 读取浮点参数；缺失或无法解析时返回 0。 */
    protected static double dbl(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0d;
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (NumberFormatException e) { return 0d; }
    }

    /** 读取整数参数；缺失或无法解析时返回 0。 */
    protected static int intVal(Map<String, Object> args, String key) {
        return (int) dbl(args, key);
    }

    /** 读取整数参数，支持默认值。 */
    protected static int intVal(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return defaultValue;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    /** 读取布尔参数；缺失时返回 false。 */
    protected static boolean bool(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Boolean b) return b;
        return v != null && "true".equalsIgnoreCase(String.valueOf(v));
    }

    // ── JSON Schema builder ────────────────────────────────────────────

    /**
     * 构建 JSON Schema 的 {@code object} 类型。这是工具参数最常用的结构，
     * 可以避免手写 {@code Map.of(...)} 嵌套。
     */
    protected static SchemaBuilder objectSchema() {
        return new SchemaBuilder("object");
    }

    /** 构建 JSON Schema 字符串属性。 */
    protected static Map<String, Object> stringSchema(String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "string");
        if (description != null) m.put("description", description);
        return m;
    }

    /** 构建 JSON Schema 浮点属性。 */
    protected static Map<String, Object> numberSchema(String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "number");
        if (description != null) m.put("description", description);
        return m;
    }

    /** 构建 JSON Schema 整数属性。 */
    protected static Map<String, Object> integerSchema(String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "integer");
        if (description != null) m.put("description", description);
        return m;
    }

    /** 构建 JSON Schema 布尔属性。 */
    protected static Map<String, Object> booleanSchema(String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "boolean");
        if (description != null) m.put("description", description);
        return m;
    }

    /** 构建带枚举约束的字符串属性。 */
    protected static Map<String, Object> enumSchema(String description, List<String> values) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "string");
        m.put("enum", List.copyOf(values));
        if (description != null) m.put("description", description);
        return m;
    }

    /**
     * 可变 Schema 构建器，支持链式调用 {@code .prop().required().build()}。
     */
    protected static final class SchemaBuilder {
        private final Map<String, Object> schema = new HashMap<>();
        private final Map<String, Object> properties = new HashMap<>();
        private final java.util.List<String> required = new java.util.ArrayList<>();

        SchemaBuilder(String type) {
            schema.put("type", type);
        }

    /** 在指定 key 下添加属性。 */
        public SchemaBuilder prop(String key, Map<String, Object> propertySchema) {
            properties.put(key, propertySchema);
            return this;
        }

    /** 标记属性为必填。 */
        public SchemaBuilder required(String key) {
            required.add(key);
            return this;
        }

    /** 标记多个属性为必填。 */
        public SchemaBuilder required(String... keys) {
            for (String k : keys) required.add(k);
            return this;
        }

    /** 构建不可变 Schema Map。 */
        public Map<String, Object> build() {
            if (!properties.isEmpty()) schema.put("properties", Map.copyOf(properties));
            if (!required.isEmpty()) schema.put("required", List.copyOf(required));
            return Map.copyOf(schema);
        }
    }
}
