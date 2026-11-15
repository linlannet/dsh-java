package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import java.util.List;

/**
 * {@link DeepSeekAdapter} 的配置项。
 * <p>
 * 对应 TS 侧 {@code DeepSeekConnectionOptions} 和 {@code DeepSeekAdapterOptions}。
 *
 * <p>调用流程：模型请求 → HTTP 客户端 → 解析 JSON/SSE → 归一化为领域事件。</p>
 * <p>示例：上游返回 token delta 时，适配器转换为 LLM 增量事件。</p>
 *
 */
public record DeepSeekAdapterOptions(
        String baseUrl,
        String apiKey,
        int maxTokens,
        int defaultContextWindow,
        int streamIdleTimeoutMs,
        List<DirectSeekCatalogModel> models,
        ResolvedRetryPolicy retryPolicy,
        long filesApiTimeoutMs,
        long filesConnectTimeoutMs
) {
    /** 文件上传请求默认超时（20 秒）。 */
    public static final long DEFAULT_FILES_API_TIMEOUT_MS = 20_000L;
    /** 文件上传连接默认超时（5 秒）。 */
    public static final long DEFAULT_FILES_CONNECT_TIMEOUT_MS = 5_000L;

    /**
     * 兼容旧调用：文件上传使用默认超时。
     */
    public DeepSeekAdapterOptions(String baseUrl, String apiKey, int maxTokens, int defaultContextWindow,
                                  int streamIdleTimeoutMs, List<DirectSeekCatalogModel> models,
                                  ResolvedRetryPolicy retryPolicy) {
        this(baseUrl, apiKey, maxTokens, defaultContextWindow, streamIdleTimeoutMs, models, retryPolicy,
                DEFAULT_FILES_API_TIMEOUT_MS, DEFAULT_FILES_CONNECT_TIMEOUT_MS);
    }

    public DeepSeekAdapterOptions {
        java.util.Objects.requireNonNull(baseUrl, "baseUrl");
        java.util.Objects.requireNonNull(apiKey, "apiKey");
        models = List.copyOf(models);
        filesApiTimeoutMs = filesApiTimeoutMs > 0 ? filesApiTimeoutMs : DEFAULT_FILES_API_TIMEOUT_MS;
        filesConnectTimeoutMs = filesConnectTimeoutMs > 0
                ? filesConnectTimeoutMs : DEFAULT_FILES_CONNECT_TIMEOUT_MS;
    }

    /**
     * 解析e Api Key。
     */
    public String resolveApiKey() {
        return apiKey;
    }
}
