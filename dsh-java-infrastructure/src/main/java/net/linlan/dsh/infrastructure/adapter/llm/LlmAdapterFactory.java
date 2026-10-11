package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.channel.model.valobj.AuthScheme;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPreset;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPresets;
import net.linlan.dsh.domain.channel.model.valobj.ChannelProtocol;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.anthropic.AnthropicAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import net.linlan.dsh.infrastructure.adapter.llm.openai.OpenAiCompatibleAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * LLM 适配器工厂。
 * <p>
 * 按「渠道协议」为任意 provider 动态创建适配器，参考 WaLiAPI 的 get_adaptor 工厂：
 * 新增渠道不再需要注册新 Bean——只要在模型设置中写入 providerCode + protocol，
 * 运行时会按协议路由到对应的协议适配器实现。
 *
 * <p>协议路由规则：</p>
 * <ul>
 *   <li>{@code openai}（含空值/未知值）→ {@link OpenAiCompatibleAdapter}（OpenAI 兼容协议）；</li>
 *   <li>{@code anthropic} → {@link AnthropicAdapter}（Messages 协议）；</li>
 *   <li>{@code ollama} → {@link OpenAiCompatibleAdapter}（Ollama 的 /v1 端点兼容 OpenAI 协议，
 *       且允许空 API Key）。</li>
 * </ul>
 *
 *
 */
@Component
public class LlmAdapterFactory {

    private final ObjectMapper objectMapper;

    public LlmAdapterFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定 provider 创建按协议路由的适配器。
     *
     * @param provider provider 编码（如 deepseek、qwen、anthropic、任意自定义编码）
     * @return 协议路由适配器
     */
    public LlmAdapter create(String provider) {
        ChannelPreset preset = ChannelPresets.findById(provider).orElse(null);
        String defaultBaseUrl = preset != null ? preset.baseUrl() : "";
        AuthScheme authScheme = preset != null ? preset.authScheme() : AuthScheme.BEARER;
        // 本地渠道（Ollama）允许空 key，上游不校验；占位 key 仅用于通过鉴权头构造
        String defaultApiKey = authScheme == AuthScheme.NONE ? "ollama" : "";
        String defaultProtocol = preset != null ? preset.protocol().code() : ChannelProtocol.OPENAI.code();
        return new ProtocolRoutingAdapter(provider, defaultBaseUrl, defaultApiKey, defaultProtocol, this);
    }

    /**
     * 按协议编码创建实际的协议适配器。
     *
     * @param provider provider 编码
     * @param protocol 协议编码（openai / anthropic / ollama）
     * @return 协议适配器实例
     */
    LlmAdapter createForProtocol(String provider, String protocol) {
        ChannelProtocol resolved = ChannelProtocol.fromCode(protocol);
        return switch (resolved) {
            case ANTHROPIC -> new AnthropicAdapter(provider, objectMapper);
            case OPENAI, OLLAMA -> {
                // Ollama 原生 /v1 端点兼容 OpenAI 协议；模型列表走 /api/tags 由发现服务分派
                var options = new DeepSeekAdapterOptions(
                        defaultBaseUrlOf(provider), defaultApiKeyOf(provider),
                        8_192, 128_000, DeepSeekAdapterOptionsDefaults.STREAM_IDLE_TIMEOUT_MS,
                        List.of(), null);
                yield new OpenAiCompatibleAdapter(provider, options, objectMapper);
            }
        };
    }

    private static String defaultBaseUrlOf(String provider) {
        return ChannelPresets.findById(provider).map(ChannelPreset::baseUrl).orElse("");
    }

    private static String defaultApiKeyOf(String provider) {
        return ChannelPresets.findById(provider)
                .map(p -> p.authScheme() == AuthScheme.NONE ? "ollama" : "")
                .orElse("");
    }

    /**
     * 常量兜底，避免与 DeepSeekAdapter 形成循环依赖。
     */
    private static final class DeepSeekAdapterOptionsDefaults {
        private static final int STREAM_IDLE_TIMEOUT_MS = 300_000;
    }
}
