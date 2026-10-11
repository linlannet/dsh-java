package net.linlan.dsh.domain.llm.model.valobj;

/**
 * LlmFailure 表示LlmFailure。
 */
public record LlmFailure(
        String message,
        String code,
        Integer status,
        Long providerRetryAfterMs,
        String requestId
) {
    public LlmFailure {
        java.util.Objects.requireNonNull(message, "message");
        java.util.Objects.requireNonNull(code, "code");
    }

    /**
     * 构造LlmFailure领域对象。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     */
    public LlmFailure(String message, String code) {
        this(message, code, null, null, null);
    }
}
