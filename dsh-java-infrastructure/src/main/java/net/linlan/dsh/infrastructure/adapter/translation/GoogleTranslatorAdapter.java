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
 * GoogleTranslatorAdapter 可选翻译服务适配器，经 Bundle 设置选择。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。翻译服务不可用时返回 empty，由领域侧回退原文。</p>
 *
 */
public class GoogleTranslatorAdapter implements ITranslatorPort {

    public static final String DEFAULT_ENDPOINT = "https://translation.googleapis.com/language/translate/v2";

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int REQUEST_TIMEOUT_SEC = 30;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final String endpoint;
    private final String apiKey;

    public GoogleTranslatorAdapter(String apiKey) {
        this(DEFAULT_ENDPOINT, apiKey);
    }

    public GoogleTranslatorAdapter(String endpoint, String apiKey) {
        this.endpoint = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint;
        this.apiKey = apiKey;
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
            var root = mapper.createObjectNode();
            root.put("q", text);
            root.put("target", targetLang);
            root.put("format", "text");
            String uri = endpoint + (endpoint.contains("?") ? "&" : "?")
                    + "key=" + URLEncoder.encode(apiKey == null ? "" : apiKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("Content-Type", "application/json")
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
            JsonNode translations = root.path("data").path("translations");
            if (translations.isArray() && translations.size() > 0) {
                String translated = translations.get(0).path("translatedText").asText("");
                return translated.isBlank() ? Optional.empty() : Optional.of(translated);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}