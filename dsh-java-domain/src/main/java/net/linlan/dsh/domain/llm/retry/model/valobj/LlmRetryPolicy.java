package net.linlan.dsh.domain.llm.retry.model.valobj;

import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;

import java.util.List;
import java.util.Objects;

/**
 * LlmRetryPolicy 表示 LLM 请求重试策略。
 * <p>定义最大重试次数、可重试错误码集合及备用 Provider 路由。
 */
public final class LlmRetryPolicy {

    private final int maxAttempts;
    private final List<String> retryableCodes;
    private final String fallbackProvider;

    public LlmRetryPolicy() {
        this(3, LlmErrorCodes.DEFAULT_RETRYABLE_CODES, null);
    }

    public LlmRetryPolicy(int maxAttempts, List<String> retryableCodes, String fallbackProvider) {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("maxAttempts must not be negative");
        }
        this.maxAttempts = maxAttempts;
        this.retryableCodes = List.copyOf(Objects.requireNonNullElse(retryableCodes, List.of()));
        this.fallbackProvider = fallbackProvider;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public List<String> retryableCodes() {
        return retryableCodes;
    }

    public String fallbackProvider() {
        return fallbackProvider;
    }

    /**
     * 判断给定错误和尝试次数是否应重试。
     *
     * @param errorCode 错误码
     * @param attempt   当前尝试次数（从 1 开始）
     * @return true 表示应重试
     */
    public boolean shouldRetry(String errorCode, int attempt) {
        if (attempt >= maxAttempts) {
            return false;
        }
        return retryableCodes.contains(errorCode);
    }

    /**
     * 返回备用 Provider 名称（可能为 null）。
     *
     * @param errorCode 错误码
     * @return 备用 Provider 名称，或 null 表示无备用
     */
    public String fallbackProvider(String errorCode) {
        if (fallbackProvider == null) {
            return null;
        }
        return retryableCodes.contains(errorCode) ? fallbackProvider : null;
    }
}
