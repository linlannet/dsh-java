package net.linlan.dsh.cases.runtime.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.DiscoverModelsRequestDTO;
import net.linlan.dsh.cases.support.CaseGuard;
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
 * 上游模型同步节点。
 * <p>
 * 流程：校验请求 → 按渠道协议分派模型列表接口 → 解析并返回模型编码列表。
 * 协议分派规则（参考 WaLiAPI 的 fetch_upstream_models）：
 * <ul>
 *   <li>{@code openai} → {@code GET {base}/models}，解析 {@code data[].id}；</li>
 *   <li>{@code anthropic} → {@code GET {base}/models}，携带 x-api-key + anthropic-version；</li>
 *   <li>{@code ollama} → {@code GET {base}/api/tags}，解析 {@code models[].name}。</li>
 * </ul>
 * 任何失败只向上抛错提示，绝不以空结果覆盖已有配置。
 *
 *
 */
@Service
public class ModelDiscoverySyncNode
        extends AbstractStrategyRouter<DiscoverModelsRequestDTO, NoContext, List<String>> {

    private static final Logger log = LoggerFactory.getLogger(ModelDiscoverySyncNode.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ModelDiscoverySyncNode(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 执行当前节点核心逻辑：按协议分派同步上游模型列表。
     */
    @Override
    protected List<String> doApply(DiscoverModelsRequestDTO request, NoContext context) {
        CaseGuard.requireNonNull(request, "discover models request must not be null");
        CaseGuard.requireText(request.baseUrl(), "Base URL 不能为空");

        String normalizedBase = request.baseUrl().trim();
        String apiKey = request.apiKeyRef();
        ChannelProtocol protocol = ChannelProtocol.fromCode(request.protocol());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(protocol == ChannelProtocol.OLLAMA
                        ? resolveOllamaTagsUri(normalizedBase)
                        : OpenAiCompatibleUriResolver.resolveModelsUri(normalizedBase))
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
                        "Upstream returned HTTP " + response.statusCode() + ": " + extractError(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<String> models = protocol == ChannelProtocol.OLLAMA
                    ? parseOllamaTags(root)
                    : parseOpenAiModels(root);
            return models.stream().sorted().toList();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to discover models from {} (protocol={}): {}", normalizedBase, protocol.code(), e.getMessage());
            throw new IllegalStateException("无法连接到上游服务: " + e.getMessage(), e);
        }
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<DiscoverModelsRequestDTO, NoContext, List<String>> getNext(
            DiscoverModelsRequestDTO request, NoContext context) {
        return defaultStrategyHandler;
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

    /**
     * Ollama 原生模型列表端点：{base}/api/tags。
     */
    private URI resolveOllamaTagsUri(String baseUrl) {
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        if (normalized.endsWith("/api/tags")) {
            return URI.create(normalized);
        }
        // 允许用户误填 OpenAI 兼容的 /v1 地址：剥掉 /v1 再拼原生端点
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return URI.create(normalized + "/api/tags");
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
