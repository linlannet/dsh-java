package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.channel.model.valobj.AuthScheme;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPreset;
import net.linlan.dsh.domain.channel.model.valobj.ChannelPresets;
import net.linlan.dsh.domain.channel.model.valobj.ChannelProtocol;
import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.anthropic.AnthropicAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAuthProvider;
import net.linlan.dsh.infrastructure.adapter.llm.openai.OpenAiCompatibleAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.piai.PiAiAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DeepSeekAuthProvider deepSeekAuthProvider;

    /** 兼容手工组装；生产 Spring 装配使用带认证提供方的构造器。 */
    public LlmAdapterFactory(ObjectMapper objectMapper) {
        this(objectMapper, DeepSeekAuthProvider.bearer(""));
    }

    @Autowired
    public LlmAdapterFactory(ObjectMapper objectMapper, DeepSeekAuthProvider deepSeekAuthProvider) {
        this.objectMapper = objectMapper;
        this.deepSeekAuthProvider = DeepSeekAuthProvider.require(deepSeekAuthProvider);
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
        String defaultProtocol = preset != null ? preset.protocol().code() : ChannelProtocol.OPENAI.code();
        return new ProtocolRoutingAdapter(provider, defaultBaseUrl, authProviderOf(provider), defaultProtocol, this);
    }

    /**
     * 按协议编码创建实际的协议适配器。
     *
     * @param provider provider 编码
     * @param protocol 协议编码（openai / anthropic / ollama）
     * @return 协议适配器实例
     */
    LlmAdapter createForProtocol(String provider, String protocol) {
        return createForProtocol(provider, protocol, authProviderOf(provider));
    }

    /** 使用路由已经挂载的认证提供方创建协议适配器。 */
    LlmAdapter createForProtocol(String provider, String protocol, DeepSeekAuthProvider authProvider) {
        ChannelProtocol resolved = ChannelProtocol.fromCode(protocol);
        return switch (resolved) {
            case ANTHROPIC -> new AnthropicAdapter(provider, objectMapper);
            case PI_AI -> {
                var options = new DeepSeekAdapterOptions(
                        defaultBaseUrlOf(provider), "",
                        8_192, 128_000, DeepSeekAdapterOptionsDefaults.STREAM_IDLE_TIMEOUT_MS,
                        List.of(), null);
                yield new PiAiAdapter(options, objectMapper, authProvider);
            }
            case OPENAI, OLLAMA -> {
                // Ollama 原生 /v1 端点兼容 OpenAI 协议；模型列表走 /api/tags 由发现服务分派
                var options = new DeepSeekAdapterOptions(
                        defaultBaseUrlOf(provider), "",
                        8_192, 128_000, DeepSeekAdapterOptionsDefaults.STREAM_IDLE_TIMEOUT_MS,
                        List.of(), null);
                yield new OpenAiCompatibleAdapter(provider, options, objectMapper, authProvider);
            }
        };
    }

    private DeepSeekAuthProvider authProviderOf(String provider) {
        if ("deepseek".equalsIgnoreCase(provider)) {
            return deepSeekAuthProvider;
        }
        AuthScheme authScheme = ChannelPresets.findById(provider)
                .map(ChannelPreset::authScheme)
                .orElse(AuthScheme.BEARER);
        return DeepSeekAuthProvider.bearer(authScheme == AuthScheme.NONE ? "ollama" : "");
    }

    private static String defaultBaseUrlOf(String provider) {
        return ChannelPresets.findById(provider).map(ChannelPreset::baseUrl).orElse("");
    }

    /**
     * 常量兜底，避免与 DeepSeekAdapter 形成循环依赖。
     */
    private static final class DeepSeekAdapterOptionsDefaults {
        private static final int STREAM_IDLE_TIMEOUT_MS = 300_000;
    }
}
