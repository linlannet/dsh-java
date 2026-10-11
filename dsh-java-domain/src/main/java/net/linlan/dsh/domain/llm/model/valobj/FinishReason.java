package net.linlan.dsh.domain.llm.model.valobj;

/**
 * Why a model response stopped.
 * <p>
 * Sealed hierarchy mirroring TS {@code FinishReason}. Adapters map
 * provider-specific stop reasons into these variants.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public sealed interface FinishReason permits
        FinishReason.Stop,
        FinishReason.ToolCalls,
        FinishReason.MaxTokens,
        FinishReason.Aborted,
        FinishReason.Error {

    /**
     * Stop 表示Stop。
     */
    record Stop() implements FinishReason {}

    /**
     * ToolCalls 表示ToolCallsCall列表。
     */
    record ToolCalls() implements FinishReason {}

    /**
     * MaxTokens 表示MaxTokensToken列表。
     */
    record MaxTokens() implements FinishReason {}

    /**
     * Aborted 表示Aborted。
     */
    record Aborted(LlmFailure failure) implements FinishReason {
        public Aborted {
            java.util.Objects.requireNonNull(failure, "failure");
        }
    }

    /**
     * Error 表示Error。
     */
    record Error(LlmFailure failure) implements FinishReason {
        public Error {
            java.util.Objects.requireNonNull(failure, "failure");
        }
    }

    /**
     * 判断Failure是否成立。
     * @return 领域处理结果。
     */
    default boolean isFailure() {
        return this instanceof Aborted || this instanceof Error;
    }
}
