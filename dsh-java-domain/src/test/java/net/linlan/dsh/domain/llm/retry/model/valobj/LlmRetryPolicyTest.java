package net.linlan.dsh.domain.llm.retry.model.valobj;

import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmRetryPolicy 重试策略")
class LlmRetryPolicyTest {

    @Test
    @DisplayName("默认构造 maxAttempts=3")
    void defaultConstructor() {
        var policy = new LlmRetryPolicy();
        assertEquals(3, policy.maxAttempts());
        assertTrue(policy.retryableCodes().contains(LlmErrorCodes.RATE_LIMIT));
        assertNull(policy.fallbackProvider());
    }

    @Test
    @DisplayName("shouldRetry 可重试错误码且未达上限时返回 true")
    void shouldRetryRetryableCode() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), null);
        assertTrue(policy.shouldRetry(LlmErrorCodes.RATE_LIMIT, 0));
        assertTrue(policy.shouldRetry(LlmErrorCodes.RATE_LIMIT, 1));
        assertTrue(policy.shouldRetry(LlmErrorCodes.RATE_LIMIT, 2));
    }

    @Test
    @DisplayName("shouldRetry 达上限时返回 false")
    void shouldRetryMaxAttemptsReached() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), null);
        assertFalse(policy.shouldRetry(LlmErrorCodes.RATE_LIMIT, 3));
        assertFalse(policy.shouldRetry(LlmErrorCodes.RATE_LIMIT, 4));
    }

    @Test
    @DisplayName("shouldRetry 不可重试错误码返回 false")
    void shouldRetryNonRetryableCode() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), null);
        assertFalse(policy.shouldRetry(LlmErrorCodes.AUTH, 0));
        assertFalse(policy.shouldRetry(LlmErrorCodes.INVALID_REQUEST, 0));
    }

    @Test
    @DisplayName("fallbackProvider 返回备用 Provider 名称")
    void fallbackProviderReturnsName() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), "backup-provider");
        assertEquals("backup-provider", policy.fallbackProvider(LlmErrorCodes.RATE_LIMIT));
    }

    @Test
    @DisplayName("fallbackProvider 不可重试错误码返回 null")
    void fallbackProviderNonRetryableReturnsNull() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), "backup-provider");
        assertNull(policy.fallbackProvider(LlmErrorCodes.AUTH));
    }

    @Test
    @DisplayName("fallbackProvider 无备用时返回 null")
    void fallbackProviderNoFallbackReturnsNull() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), null);
        assertNull(policy.fallbackProvider(LlmErrorCodes.RATE_LIMIT));
    }

    @Test
    @DisplayName("maxAttempts 负数抛异常")
    void negativeMaxAttemptsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LlmRetryPolicy(-1, List.of(), null));
    }
}
