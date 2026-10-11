package net.linlan.dsh.domain.llm.model.valobj;

/**
 * Harness-owned LLM error codes.
 * <p>
 * Stable codes used across retry policies, adapter failures, and diagnostics.
 * Mirrors TS {@code LlmError} code constants.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public final class LlmErrorCodes {
    /**
     * 处理LLM Error Codes。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private LlmErrorCodes() {}

    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String CONTEXT_WINDOW_EXCEEDED = "CONTEXT_WINDOW_EXCEEDED";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String SERVER = "SERVER";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String TRANSPORT = "TRANSPORT";
    public static final String AUTH = "AUTH";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String MALFORMED_RESPONSE = "MALFORMED_RESPONSE";
    public static final String STREAM_CLOSED = "STREAM_CLOSED";
    public static final String ABORTED = "ABORTED";
    public static final String UNKNOWN = "UNKNOWN";

    /** 普通重试策略默认可重试的错误码。 */
    public static final java.util.List<String> DEFAULT_RETRYABLE_CODES = java.util.List.of(
            EMPTY_RESPONSE, RATE_LIMIT, SERVER, TIMEOUT, TRANSPORT
    );
}
