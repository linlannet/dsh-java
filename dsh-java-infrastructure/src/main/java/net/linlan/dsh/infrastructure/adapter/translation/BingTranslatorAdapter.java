package net.linlan.dsh.infrastructure.adapter.translation;

import net.linlan.dsh.domain.translation.cot.adapter.port.ITranslatorPort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * BingTranslatorAdapter 默认翻译服务适配器。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。翻译服务不可用时返回 empty，由领域侧回退原文。</p>
 *
 */
public class BingTranslatorAdapter implements ITranslatorPort {

    public static final String DEFAULT_ENDPOINT = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0";

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int REQUEST_TIMEOUT_SEC = 30;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final String endpoint;
    private final String subscriptionKey;

    public BingTranslatorAdapter(String subscriptionKey) {
        this(DEFAULT_ENDPOINT, subscriptionKey);
    }

    public BingTranslatorAdapter(String endpoint, String subscriptionKey) {
        this.endpoint = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint;
        this.subscriptionKey = subscriptionKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
                .build();
    }

    @Override
    public Optional<String> translate(String text, String targetLang) {
        if (text == null || text.isBlank() || targetLang == null || targetLang.isBlank()) {
            return Optional.empty();
        }
        try {
            String uri = endpoint + (endpoint.contains("?") ? "&" : "?")
                    + "to=" + URLEncoder.encode(targetLang, StandardCharsets.UTF_8);
            var root = mapper.createArrayNode();
            root.add(mapper.createObjectNode().put("Text", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("Content-Type", "application/json")
                    .header("Ocp-Apim-Subscription-Key", subscriptionKey == null ? "" : subscriptionKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(body);
            if (root.isArray() && root.size() > 0) {
                JsonNode translations = root.get(0).path("translations");
                if (translations.isArray() && translations.size() > 0) {
                    String translated = translations.get(0).path("text").asText("");
                    return translated.isBlank() ? Optional.empty() : Optional.of(translated);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}