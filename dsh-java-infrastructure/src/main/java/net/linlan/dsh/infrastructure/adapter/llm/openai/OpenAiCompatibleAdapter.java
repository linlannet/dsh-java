package net.linlan.dsh.infrastructure.adapter.llm.openai;

import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generic OpenAI-compatible adapter that extends the DeepSeek adapter
 * for any OpenAI-compatible endpoint (OpenAI, Ollama, Groq, Together, etc.).
 * Only overrides the provider identity; request/response format is identical.
 *
 *
 */
public class OpenAiCompatibleAdapter extends DeepSeekAdapter {

    private final String providerId;

    /**
     * 打开 Ai Compatible Adapter。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param providerId 模型提供方 ID参数。
     * @param options Agent 配置。
     * @param objectMapper object Mapper参数。
     * @return 方法执行结果。
     */
    public OpenAiCompatibleAdapter(String providerId, DeepSeekAdapterOptions options, ObjectMapper objectMapper) {
        super(options, objectMapper);
        this.providerId = providerId;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public LlmProviderInfo providerInfo(String provider) {
        return new LlmProviderInfo(provider, capitalize(provider));
    }

    /**
     * 处理capitalize。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param s s参数。
     * @return 方法执行结果。
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
