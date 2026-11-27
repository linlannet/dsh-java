package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;

import java.net.http.HttpRequest;
import java.util.Objects;

/** 为 DeepSeek 请求解析凭据并签署 HTTP 请求。 */
@FunctionalInterface
public interface DeepSeekAuthProvider {

    /**
     * 解析一次请求使用的凭据。
     *
     * @param options 当前模型请求；模型目录等无请求上下文的调用传入 {@code null}
     * @return 已解析凭据，缺失时返回 {@code null} 或空串
     */
    String resolveCredential(GenerateOptions options);

    /**
     * 使用解析后的凭据签署请求。
     *
     * @param builder 待签署请求
     * @param options 当前模型请求
     * @return 同一个请求构造器
     */
    default HttpRequest.Builder sign(HttpRequest.Builder builder, GenerateOptions options) {
        String credential = resolveCredential(options);
        if (credential == null || credential.isBlank()) {
            throw DeepSeekAuthError.missingCredential();
        }
        builder.header("Authorization", "Bearer " + credential.trim());
        return builder;
    }

    /**
     * 创建 Bearer 凭据提供方；请求中的凭据优先于静态默认值。
     */
    static DeepSeekAuthProvider bearer(String configuredApiKey) {
        return options -> options != null && options.apiKey() != null && !options.apiKey().isBlank()
                ? options.apiKey() : configuredApiKey;
    }

    /** 启动装配时要求显式提供认证提供方。 */
    static DeepSeekAuthProvider require(DeepSeekAuthProvider provider) {
        if (provider == null) {
            throw DeepSeekAuthError.missingProvider();
        }
        return provider;
    }

    /** 兼容旧构造入口，将静态密钥转换为提供方。 */
    static DeepSeekAuthProvider from(DeepSeekAdapterOptions options) {
        Objects.requireNonNull(options, "options");
        return bearer(options.apiKey());
    }
}
