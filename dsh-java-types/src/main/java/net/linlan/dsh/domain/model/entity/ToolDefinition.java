package net.linlan.dsh.domain.model.entity;

import net.linlan.dsh.domain.model.valobj.ContentBlock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 已注册工具，包含 Schema 和执行函数。
 * 对应 TS 侧 {@code ToolDefinition}。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ToolDefinition {

    /** 工具名称，在注册表内唯一。 */
    String name();

    /** 发送给模型的人类可读描述。 */
    String description();

    /** 工具参数的 JSON Schema。 */
    Map<String, Object> parameters();

    /** 工具输出的 JSON Schema。默认宽松 schema，子类可覆盖收紧。 */
    default Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of()
        );
    }

    /**
     * 参数校验通过后执行工具。
     * @param args 模型参数的无损快照，不可再修改
     * @param exec 执行身份、取消信号和上下文延迟
     * @return 标准化执行结果
     */
    CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext exec);

    /**
     * 判断本次调用是否可以进入并行组。
     * 只有返回 {@code true} 才会启用并行，默认串行。
     */
    default boolean isConcurrencySafe(Object args) {
        return false;
    }

    /**
     * 在发送给模型前对内容做同步后置转换。
     * 返回空集合表示保留原内容。
     */
    default Optional<List<ContentBlock>> finalizeContent(ToolExecution exec, ToolExecutionResult result) {
        return Optional.empty();
    }

    /**
     * 可选的协作式超时时间，单位毫秒。
     * 返回 0 表示没有截止时间。
     */
    default long timeoutMs() {
        return 0;
    }
}
