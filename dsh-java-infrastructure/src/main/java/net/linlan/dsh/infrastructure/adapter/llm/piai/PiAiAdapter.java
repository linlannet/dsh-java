package net.linlan.dsh.infrastructure.adapter.llm.piai;

import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import net.linlan.dsh.infrastructure.adapter.llm.openai.OpenAiCompatibleAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PiAiAdapter 是 Pi-AI Provider 的 LLM 适配器。
 * <p>继承 {@link OpenAiCompatibleAdapter}，与 DeepSeek 共享 OpenAI 兼容核心协议。
 * 仅覆盖 Provider 标识，请求/响应格式与 OpenAI 兼容协议一致。
 */
public final class PiAiAdapter extends OpenAiCompatibleAdapter {

    private static final String PROVIDER_ID = "pi-ai";

    /**
     * 创建 Pi-AI 适配器。
     *
     * @param options      适配器配置
     * @param objectMapper JSON 序列化器
     */
    public PiAiAdapter(DeepSeekAdapterOptions options, ObjectMapper objectMapper) {
        super(PROVIDER_ID, options, objectMapper);
    }

    /**
     * 返回 Provider 标识。
     */
    public String providerId() {
        return PROVIDER_ID;
    }
}
