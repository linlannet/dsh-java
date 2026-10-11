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
        ResolvedRetryPolicy retryPolicy
) {
    public DeepSeekAdapterOptions {
        java.util.Objects.requireNonNull(baseUrl, "baseUrl");
        java.util.Objects.requireNonNull(apiKey, "apiKey");
        models = List.copyOf(models);
    }

    /**
     * 解析e Api Key。
     */
    public String resolveApiKey() {
        return apiKey;
    }
}
