package net.linlan.dsh.domain.llm.retry.service;

import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.llm.model.valobj.LlmFailure;

import java.util.Set;

/**
 * RetryableErrorClassifier 对 LLM 错误进行可重试分类。
 * <p>识别 429（RATE_LIMIT）、5xx（SERVER）等可重试错误，区分不可重试错误（AUTH、INVALID_REQUEST）。
 */
public final class RetryableErrorClassifier {

    private static final Set<Integer> RETRYABLE_HTTP_STATUS = Set.of(429, 500, 502, 503, 504);

    private static final Set<String> NON_RETRYABLE_CODES = Set.of(
            LlmErrorCodes.AUTH,
            LlmErrorCodes.INVALID_REQUEST,
            LlmErrorCodes.CONTEXT_WINDOW_EXCEEDED,
            LlmErrorCodes.MALFORMED_RESPONSE
    );

    /**
     * 从异常中提取错误码。
     *
     * @param throwable 异常
     * @return 错误码，未知异常返回 UNKNOWN
     */
    public String classify(Throwable throwable) {
        if (throwable == null) {
            return LlmErrorCodes.UNKNOWN;
        }
        String msg = throwable.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out")) {
                return LlmErrorCodes.TIMEOUT;
            }
            if (lower.contains("connection") || lower.contains("transport") || lower.contains("i/o")) {
                return LlmErrorCodes.TRANSPORT;
            }
            if (lower.contains("429") || lower.contains("rate limit")) {
                return LlmErrorCodes.RATE_LIMIT;
            }
            if (lower.contains("quota")) {
                return LlmErrorCodes.QUOTA_EXCEEDED;
            }
            if (lower.contains("auth") || lower.contains("unauthorized") || lower.contains("forbidden")) {
                return LlmErrorCodes.AUTH;
            }
            if (lower.contains("invalid")) {
                return LlmErrorCodes.INVALID_REQUEST;
            }
            if (lower.contains("context window") || lower.contains("context_length")) {
                return LlmErrorCodes.CONTEXT_WINDOW_EXCEEDED;
            }
            if (lower.contains("empty response")) {
                return LlmErrorCodes.EMPTY_RESPONSE;
            }
            if (lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")) {
                return LlmErrorCodes.SERVER;
            }
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            return classify(throwable.getCause());
        }
        return LlmErrorCodes.UNKNOWN;
    }

    /**
     * 判断错误码是否可重试。
     *
     * @param code 错误码
     * @return true 表示可重试
     */
    public boolean isRetryable(String code) {
        if (code == null) {
            return false;
        }
        if (NON_RETRYABLE_CODES.contains(code)) {
            return false;
        }
        return LlmErrorCodes.DEFAULT_RETRYABLE_CODES.contains(code);
    }

    /**
     * 从 HTTP 状态码判断是否可重试。
     *
     * @param status HTTP 状态码
     * @return true 表示可重试
     */
    public boolean isRetryableStatus(int status) {
        return RETRYABLE_HTTP_STATUS.contains(status);
    }

    /**
     * 从异常判断是否可重试。
     *
     * @param throwable 异常
     * @return true 表示可重试
     */
    public boolean isRetryable(Throwable throwable) {
        return isRetryable(classify(throwable));
    }
}
