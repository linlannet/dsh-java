package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.channel.model.valobj.ChannelProtocol;
import net.linlan.dsh.domain.channel.service.OpenAiCompatibleUriResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 上游模型同步服务。
 * <p>
 * 按渠道协议分派模型列表接口（参考 WaLiAPI 的 fetch_upstream_models）：
 * <ul>
 *   <li>{@code openai} → {@code GET {base}/models}，解析 {@code data[].id}；</li>
 *   <li>{@code anthropic} → {@code GET {base}/models}（base 自带 /v1），携带 x-api-key + anthropic-version；</li>
 *   <li>{@code ollama} → {@code GET {base}/api/tags}，解析 {@code models[].name}。</li>
 * </ul>
 * 失败时抛出异常由调用方提示，绝不以空结果覆盖已有配置。
 *
 *
 */
@Service
public class UpstreamModelSyncService {

    private static final Logger log = LoggerFactory.getLogger(UpstreamModelSyncService.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public UpstreamModelSyncService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 从上游渠道同步可用模型列表。
     *
     * @param baseUrl  渠道基础地址
     * @param apiKey   API Key（可为空，本地渠道无需鉴权）
     * @param protocol 渠道协议编码（openai / anthropic / ollama），空值按 openai 处理
     * @return 已排序的模型编码列表
     */
    public List<String> syncModels(String baseUrl, String apiKey, String protocol) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }
        ChannelProtocol resolved = ChannelProtocol.fromCode(protocol);
        return switch (resolved) {
            case OLLAMA -> fetch(baseUrl, apiKey, resolved, this::resolveOllamaTagsUri, this::parseOllamaTags);
            case ANTHROPIC -> fetch(baseUrl, apiKey, resolved, OpenAiCompatibleUriResolver::resolveModelsUri, this::parseOpenAiModels);
            case OPENAI, PI_AI -> fetch(baseUrl, apiKey, resolved, OpenAiCompatibleUriResolver::resolveModelsUri, this::parseOpenAiModels);
        };
    }

    /**
     * 发起请求并按解析器提取模型列表。
     */
    private List<String> fetch(String baseUrl, String apiKey, ChannelProtocol protocol,
                               java.util.function.Function<String, URI> uriResolver,
                               java.util.function.Function<JsonNode, List<String>> parser) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uriResolver.apply(baseUrl.trim()))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            if (protocol == ChannelProtocol.ANTHROPIC) {
                builder.header("x-api-key", apiKey);
                builder.header("anthropic-version", ANTHROPIC_VERSION);
            } else {
                builder.header("Authorization", "Bearer " + apiKey);
            }
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "上游返回 HTTP " + response.statusCode() + ": " + extractError(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            return parser.apply(root).stream().sorted().toList();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to sync models from {} (protocol={}): {}", baseUrl, protocol.code(), e.getMessage());
            throw new IllegalStateException("无法连接到上游服务: " + e.getMessage(), e);
        }
    }

    /**
     * Ollama 原生模型列表端点：{base}/api/tags。
     */
    private URI resolveOllamaTagsUri(String baseUrl) {
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/api/tags")) return URI.create(normalized);
        // 允许用户误填 OpenAI 兼容的 /v1 地址：剥掉 /v1 再拼原生端点
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return URI.create(normalized + "/api/tags");
    }

    /**
     * 解析 OpenAI 兼容的 {@code data[].id}（Anthropic /v1/models 结构相同）。
     */
    private List<String> parseOpenAiModels(JsonNode root) {
        JsonNode data = root.path("data");
        List<String> models = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                String id = node.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(id);
                }
            }
        }
        return models;
    }

    /**
     * 解析 Ollama {@code models[].name}。
     */
    private List<String> parseOllamaTags(JsonNode root) {
        JsonNode models = root.path("models");
        List<String> result = new ArrayList<>();
        if (models.isArray()) {
            for (JsonNode node : models) {
                String name = node.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private String extractError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText(null);
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
        } catch (Exception ignored) {
        }
        return body != null && body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
