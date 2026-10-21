package net.linlan.dsh.infrastructure.adapter.speech;

import net.linlan.dsh.domain.speech.adapter.port.ISpeechProviderPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * SenseVoiceSpeechAdapter SenseVoice 语音转写 Provider 适配器。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。Provider 端点不可用时返回 Provider 不可用。</p>
 *
 */
public class SenseVoiceSpeechAdapter implements ISpeechProviderPort {

    private static final String PROVIDER_NAME = "sensevoice";
    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int REQUEST_TIMEOUT_SEC = 60;

    private final String endpoint;
    private final String apiKey;
    private final HttpClient client;

    public SenseVoiceSpeechAdapter(String endpoint) {
        this(endpoint, null);
    }

    public SenseVoiceSpeechAdapter(String endpoint, String apiKey) {
        this.endpoint = endpoint == null ? "" : endpoint;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
                .build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean available() {
        return !endpoint.isBlank();
    }

    @Override
    public Optional<String> transcribe(byte[] audioData) {
        if (!available() || audioData == null || audioData.length == 0) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audioData));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                return (body == null || body.isBlank()) ? Optional.empty() : Optional.of(body.trim());
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}