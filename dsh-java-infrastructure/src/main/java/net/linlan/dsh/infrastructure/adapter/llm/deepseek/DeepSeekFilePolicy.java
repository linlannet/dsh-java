package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

/** 已解析的 DeepSeek 文件上传策略。 */
public record DeepSeekFilePolicy(
        long expiresAfterSeconds,
        long refreshMarginSeconds,
        int quotaCleanupBatch,
        int uploadConcurrency,
        long uploadTimeoutMs,
        int uploadMaxRetries,
        long uploadRetryDelayMs
) {
    public static final long DEFAULT_EXPIRES_AFTER_SECONDS = 7 * 24 * 60 * 60L;
    public static final long DEFAULT_REFRESH_MARGIN_SECONDS = 60 * 60L;
    public static final int DEFAULT_QUOTA_CLEANUP_BATCH = 100;
    public static final int DEFAULT_UPLOAD_CONCURRENCY = 3;
    public static final int DEFAULT_UPLOAD_MAX_RETRIES = 2;
    public static final long DEFAULT_UPLOAD_RETRY_DELAY_MS = 500L;

    public DeepSeekFilePolicy {
        if (expiresAfterSeconds < 3_600 || expiresAfterSeconds > 2_592_000) {
            throw invalid("expiresAfterSeconds must be from 3600 through 2592000");
        }
        if (refreshMarginSeconds < 0 || refreshMarginSeconds >= expiresAfterSeconds) {
            throw invalid("refreshMarginSeconds must be non-negative and below expiresAfterSeconds");
        }
        if (quotaCleanupBatch < 1 || quotaCleanupBatch > 1_000) {
            throw invalid("quotaCleanupBatch must be from 1 through 1000");
        }
        if (uploadConcurrency < 1 || uploadConcurrency > 3) {
            throw invalid("uploadConcurrency must be from 1 through 3");
        }
        if (uploadTimeoutMs <= 0) {
            throw invalid("uploadTimeoutMs must be positive");
        }
        if (uploadMaxRetries < 0 || uploadMaxRetries > 2) {
            throw invalid("uploadMaxRetries must be from 0 through 2");
        }
        if (uploadRetryDelayMs <= 0) {
            throw invalid("uploadRetryDelayMs must be positive");
        }
    }

    public static DeepSeekFilePolicy defaults() {
        return defaults(DeepSeekAdapterOptions.DEFAULT_FILES_API_TIMEOUT_MS);
    }

    public static DeepSeekFilePolicy defaults(long uploadTimeoutMs) {
        return new DeepSeekFilePolicy(
                DEFAULT_EXPIRES_AFTER_SECONDS,
                DEFAULT_REFRESH_MARGIN_SECONDS,
                DEFAULT_QUOTA_CLEANUP_BATCH,
                DEFAULT_UPLOAD_CONCURRENCY,
                uploadTimeoutMs,
                DEFAULT_UPLOAD_MAX_RETRIES,
                DEFAULT_UPLOAD_RETRY_DELAY_MS
        );
    }

    private static DeepSeekError invalid(String message) {
        return new DeepSeekError(DeepSeekError.FILE_POLICY_INVALID, message);
    }
}
