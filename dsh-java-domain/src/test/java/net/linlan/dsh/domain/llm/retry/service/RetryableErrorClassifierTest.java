package net.linlan.dsh.domain.llm.retry.service;

import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetryableErrorClassifier 可重试错误分类")
class RetryableErrorClassifierTest {

    private final RetryableErrorClassifier classifier = new RetryableErrorClassifier();

    @Test
    @DisplayName("timeout 异常分类为 TIMEOUT")
    void timeoutClassified() {
        assertEquals(LlmErrorCodes.TIMEOUT, classifier.classify(new RuntimeException("Connection timed out")));
    }

    @Test
    @DisplayName("429 异常分类为 RATE_LIMIT")
    void rateLimitClassified() {
        assertEquals(LlmErrorCodes.RATE_LIMIT, classifier.classify(new RuntimeException("HTTP 429 rate limit exceeded")));
    }

    @Test
    @DisplayName("5xx 异常分类为 SERVER")
    void serverErrorClassified() {
        assertEquals(LlmErrorCodes.SERVER, classifier.classify(new RuntimeException("HTTP 503 service unavailable")));
    }

    @Test
    @DisplayName("auth 异常分类为 AUTH")
    void authClassified() {
        assertEquals(LlmErrorCodes.AUTH, classifier.classify(new RuntimeException("401 unauthorized")));
    }

    @Test
    @DisplayName("null 异常分类为 UNKNOWN")
    void nullClassifiedAsUnknown() {
        assertEquals(LlmErrorCodes.UNKNOWN, classifier.classify(null));
    }

    @Test
    @DisplayName("isRetryable RATE_LIMIT 返回 true")
    void rateLimitIsRetryable() {
        assertTrue(classifier.isRetryable(LlmErrorCodes.RATE_LIMIT));
    }

    @Test
    @DisplayName("isRetryable AUTH 返回 false")
    void authIsNotRetryable() {
        assertFalse(classifier.isRetryable(LlmErrorCodes.AUTH));
    }

    @Test
    @DisplayName("isRetryable INVALID_REQUEST 返回 false")
    void invalidRequestIsNotRetryable() {
        assertFalse(classifier.isRetryable(LlmErrorCodes.INVALID_REQUEST));
    }

    @Test
    @DisplayName("isRetryableStatus 429 返回 true")
    void status429IsRetryable() {
        assertTrue(classifier.isRetryableStatus(429));
    }

    @Test
    @DisplayName("isRetryableStatus 200 返回 false")
    void status200IsNotRetryable() {
        assertFalse(classifier.isRetryableStatus(200));
    }

    @Test
    @DisplayName("isRetryable(Throwable) 组合方法正确")
    void isRetryableThrowable() {
        assertTrue(classifier.isRetryable(new RuntimeException("timeout")));
        assertFalse(classifier.isRetryable(new RuntimeException("unauthorized")));
    }

    @Test
    @DisplayName("cause 链递归分类")
    void causeChainClassification() {
        var root = new RuntimeException("timeout", new RuntimeException("inner"));
        assertEquals(LlmErrorCodes.TIMEOUT, classifier.classify(root));
    }
}
