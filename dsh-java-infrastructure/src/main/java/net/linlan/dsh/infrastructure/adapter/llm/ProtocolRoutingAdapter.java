package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAuthProvider;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * 按协议动态路由的委托适配器。
 * <p>
 * 对 {@code ILlmRuntimePort} 暴露为一个固定的 provider 适配器；
 * 真正发起调用时读取请求携带的 {@code protocol}（来自模型设置），
 * 委托给工厂创建的协议适配器（OpenAI 兼容 / Anthropic / Ollama）。
 * 这样同一份代码即可服务任意数量的渠道，新增渠道无需注册新 Bean。
 *
 *
 */
public class ProtocolRoutingAdapter implements LlmAdapter {

    private final String providerId;
    private final String defaultBaseUrl;
    private final String defaultProtocol;
    private final LlmAdapterFactory factory;
    private final DeepSeekAuthProvider authProvider;

    /**
     * 兼容旧调用方；静态密钥立即封装到认证提供方，路由自身不保存或读取密钥。
     */
    public ProtocolRoutingAdapter(String providerId, String defaultBaseUrl, String defaultApiKey,
                                  String defaultProtocol, LlmAdapterFactory factory) {
        this(providerId, defaultBaseUrl, DeepSeekAuthProvider.bearer(defaultApiKey), defaultProtocol, factory);
    }

    /**
     * @param providerId      该实例服务的 provider 编码
     * @param defaultBaseUrl  模板默认地址（请求未携带 baseUrl 时使用）
     * @param authProvider    请求认证提供方
     * @param defaultProtocol 模板默认协议（请求未携带 protocol 时使用）
     * @param factory         协议适配器工厂
     */
    public ProtocolRoutingAdapter(String providerId, String defaultBaseUrl, DeepSeekAuthProvider authProvider,
                                  String defaultProtocol, LlmAdapterFactory factory) {
        this.providerId = providerId;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultProtocol = defaultProtocol;
        this.factory = factory;
        this.authProvider = DeepSeekAuthProvider.require(authProvider);
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public LlmProviderInfo providerInfo(String provider) {
        return new LlmProviderInfo(provider, provider);
    }

    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
        return delegate(options).stream(fillDefaults(options));
    }

    @Override
    public List<LlmModelInfo> listModels(String provider) {
        // 模型目录由发现服务按协议拉取；适配器本地不维护硬编码目录
        return List.of();
    }

    @Override
    public LlmResolvedModelInfo resolveModel(String provider, String model) {
        return delegate(null).resolveModel(provider, model);
    }

    @Override
    public ResolvedRetryPolicy retryPolicy(String provider) {
        return null;
    }

    /**
     * 按请求协议（缺省回落模板默认协议）解析出实际协议适配器。
     */
    private LlmAdapter delegate(GenerateOptions options) {
        String protocol = options != null && options.protocol() != null && !options.protocol().isBlank()
                ? options.protocol()
                : defaultProtocol;
        return factory.createForProtocol(providerId, protocol, authProvider);
    }

    /**
     * 补齐请求中缺省的 baseUrl / protocol；凭据由认证提供方解析。
     */
    private GenerateOptions fillDefaults(GenerateOptions options) {
        String baseUrl = options.baseUrl() != null && !options.baseUrl().isBlank()
                ? options.baseUrl() : defaultBaseUrl;
        String protocol = options.protocol() != null && !options.protocol().isBlank()
                ? options.protocol() : defaultProtocol;
        return new GenerateOptions(
                options.provider(), options.model(), options.reasoningEffort(),
                options.messages(), options.system(), options.tools(),
                options.temperature(), options.maxTokens(), options.stop(),
                options.sessionId(), options.purpose(), baseUrl, options.apiKey(), protocol
        );
    }
}
