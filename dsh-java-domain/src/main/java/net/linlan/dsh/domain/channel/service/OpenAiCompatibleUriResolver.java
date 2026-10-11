package net.linlan.dsh.domain.channel.service;

import java.net.URI;
import java.util.regex.Pattern;

public final class OpenAiCompatibleUriResolver {
    private static final Pattern VERSIONED_PATH = Pattern.compile("/v\\d+(?:[a-z]+)?(?:/|$)");

    private OpenAiCompatibleUriResolver() {
    }

    public static URI resolveModelsUri(String baseUrl) {
        return OpenAiCompatibleUriResolver.resolve(baseUrl, "/models");
    }

    public static URI resolveChatCompletionsUri(String baseUrl) {
        return OpenAiCompatibleUriResolver.resolve(baseUrl, "/chat/completions");
    }

    private static URI resolve(String baseUrl, String path) {
        String normalized;
        String string = normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith(path)) {
            return URI.create(normalized);
        }
        if (VERSIONED_PATH.matcher(normalized).find()) {
            return URI.create(normalized + path);
        }
        return URI.create(normalized + "/v1" + path);
    }
}

